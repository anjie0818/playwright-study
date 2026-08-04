package com.research.playwright;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.*;
import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * B29 - 官方文档 Navigations 章节实践
 *
 * 本章核心：
 *   1. 基本导航 — page.navigate(url)
 *   2. 页面加载完成时机 — load 事件 vs 自动等待
 *   3. 水合问题（Hydration） — 静态页面 vs 动态交互
 *   4. 等待导航 — waitForURL() 用于多步骤导航
 *   5. 导航事件 — onDOMContentLoaded / onLoad 生命周期
 *
 * 运行方式：
 *   mvn test -Dtest=B29_Navigations
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class B29_Navigations {

    private Playwright playwright;
    private Browser browser;

    @BeforeAll
    void beforeAll() {
        Map<String, String> env = new HashMap<>(System.getenv());
        String chromePath = resolveChromePath();
        if (chromePath != null) {
            env.put("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1");
        }
        playwright = Playwright.create(new Playwright.CreateOptions().setEnv(env));

        BrowserType.LaunchOptions opts = new BrowserType.LaunchOptions().setHeadless(false);
        if (chromePath != null) {
            opts.setExecutablePath(Paths.get(chromePath));
        }
        browser = playwright.chromium().launch(opts);
    }

    @AfterAll
    void afterAll() {
        try { if (browser != null) browser.close(); } catch (Exception ignored) {}
        try { if (playwright != null) playwright.close(); } catch (Exception ignored) {}
    }

    // ─────────────────────────────────────────────────────────────
    // 1. 基本导航
    // ─────────────────────────────────────────────────────────────

    @Test @Order(1)
    void basicNavigate() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();

            // 导航到 Playwright 官网
            page.navigate("https://playwright.dev/");

            // 验证 URL 和标题
            assertTrue(page.url().contains("playwright.dev"));
            assertNotNull(page.title());
            System.out.println("[Test] basicNavigate: url=" + page.url() + ", title=" + page.title());
        }
    }

    @Test @Order(2)
    void navigateAndWaitForElement() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();

            // 导航后自动等待元素可交互
            page.navigate("https://example.com");

            // getByText 会自动等待元素可见
            page.getByText("Example Domain").click();

            // 验证页面仍在
            assertThat(page.getByText("Example Domain")).isVisible();
            System.out.println("[Test] navigateAndWaitForElement: auto-wait worked");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 2. 页面加载时机：load vs domcontentloaded
    // ─────────────────────────────────────────────────────────────

    @Test @Order(3)
    void waitForLoadState() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();

            page.navigate("https://example.com");

            // 等待不同的加载状态
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            System.out.println("[Test] waitForLoadState: DOMContentLoaded fired");

            page.waitForLoadState(LoadState.LOAD);
            System.out.println("[Test] waitForLoadState: load fired");

            // networkidle 是可选的，等待网络空闲
            page.waitForLoadState(LoadState.NETWORKIDLE);
            System.out.println("[Test] waitForLoadState: networkidle fired");

            assertThat(page.getByText("Example Domain")).isVisible();
        }
    }

    @Test @Order(4)
    void navigateWithWaitUntil() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();

            // navigate 时指定 waitUntil 参数
            page.navigate("https://example.com",
                    new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));

            // DOM 已加载，可以开始交互
            assertThat(page.getByText("Example Domain")).isVisible();
            System.out.println("[Test] navigateWithWaitUntil: DOMCONTENTLOADED");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 3. 水合问题演示
    // ─────────────────────────────────────────────────────────────

    @Test @Order(5)
    void hydrationIssue() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();

            // 模拟水合问题：静态 HTML 先渲染，JS 后添加交互
            String html = """
                <!DOCTYPE html>
                <html>
                <head><title>Hydration Test</title></head>
                <body>
                    <h1>Hydration Demo</h1>
                    <button id="btn" disabled>Click Me</button>
                    <p id="result">waiting...</p>
                    <script>
                        // 模拟水合：2秒后启用按钮并添加点击监听
                        setTimeout(() => {
                            const btn = document.getElementById('btn');
                            btn.disabled = false;
                            btn.addEventListener('click', () => {
                                document.getElementById('result').textContent = 'clicked!';
                            });
                        }, 2000);
                    </script>
                </body>
                </html>
                """;

            page.setContent(html);

            // Playwright 会自动等待按钮可交互（enabled）
            page.locator("#btn").click();

            // 验证点击成功
            assertThat(page.locator("#result")).hasText("clicked!");
            System.out.println("[Test] hydrationIssue: auto-wait for enabled state");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 4. 等待导航（多步骤场景）
    // ─────────────────────────────────────────────────────────────

    @Test @Order(6)
    void waitForURL() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();

            // 用 route 模拟带链接的页面
            ctx.route("**/start", r -> r.fulfill(new Route.FulfillOptions()
                    .setContentType("text/html")
                    .setBody("<html><body><a href='/login'>Go to Login</a></body></html>")));
            ctx.route("**/login", r -> r.fulfill(new Route.FulfillOptions()
                    .setContentType("text/html")
                    .setBody("<html><body><h1>Login Page</h1></body></html>")));

            page.navigate("https://test.local/start");

            // 点击链接触发导航，waitForURL 等待新 URL
            page.getByText("Go to Login").click();
            page.waitForURL("**/login");

            assertTrue(page.url().contains("/login"));
            assertThat(page.getByText("Login Page")).isVisible();
            System.out.println("[Test] waitForURL: navigated to " + page.url());
        }
    }

    @Test @Order(7)
    void waitForURLWithPattern() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();

            ctx.route("**/start", r -> r.fulfill(new Route.FulfillOptions()
                    .setContentType("text/html")
                    .setBody("<html><body><a href='/dashboard'>Go to Dashboard</a></body></html>")));
            ctx.route("**/dashboard", r -> r.fulfill(new Route.FulfillOptions()
                    .setContentType("text/html")
                    .setBody("<html><body><h1>Dashboard</h1></body></html>")));

            page.navigate("https://test.local/start");

            // 使用正则匹配 URL
            page.getByText("Go to Dashboard").click();
            page.waitForURL(java.util.regex.Pattern.compile(".*dashboard.*"));

            assertTrue(page.url().contains("dashboard"));
            assertThat(page.getByText("Dashboard")).isVisible();
            System.out.println("[Test] waitForURLWithPattern: regex matched, url=" + page.url());
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 5. 导航事件监听
    // ─────────────────────────────────────────────────────────────

    @Test @Order(8)
    void navigationEvents() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();

            AtomicBoolean domContentLoaded = new AtomicBoolean(false);
            AtomicBoolean loaded = new AtomicBoolean(false);

            // 注册事件监听
            page.onDOMContentLoaded(e -> {
                domContentLoaded.set(true);
                System.out.println("[Event] DOMContentLoaded fired");
            });

            page.onLoad(e -> {
                loaded.set(true);
                System.out.println("[Event] load fired");
            });

            page.navigate("https://example.com");

            // 等待事件触发
            page.waitForLoadState(LoadState.LOAD);

            assertTrue(domContentLoaded.get(), "DOMContentLoaded should fire");
            assertTrue(loaded.get(), "load should fire");
            System.out.println("[Test] navigationEvents: both events fired");
        }
    }

    @Test @Order(9)
    void multipleNavigations() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();

            List<String> urls = new ArrayList<>();

            // 监听每次导航
            page.onLoad(e -> urls.add(page.url()));

            // 多次导航
            page.navigate("https://example.com");
            page.navigate("https://playwright.dev/");

            page.waitForLoadState(LoadState.LOAD);

            // 验证记录了多次导航
            assertTrue(urls.size() >= 2, "Should record multiple navigations");
            assertTrue(urls.stream().anyMatch(u -> u.contains("example.com")));
            assertTrue(urls.stream().anyMatch(u -> u.contains("playwright.dev")));

            System.out.println("[Test] multipleNavigations: recorded " + urls.size() + " navigations");
            urls.forEach(u -> System.out.println("  - " + u));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 6. 导航失败处理
    // ─────────────────────────────────────────────────────────────

    @Test @Order(10)
    void navigateToInvalidURL() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();

            // 导航到无效域名
            assertThrows(PlaywrightException.class, () -> {
                page.navigate("https://this-domain-does-not-exist-12345.com");
            });

            System.out.println("[Test] navigateToInvalidURL: exception thrown as expected");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 7. 重定向处理
    // ─────────────────────────────────────────────────────────────

    @Test @Order(11)
    void handleRedirect() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();

            // example.com 会重定向到 example.org（某些情况下）
            page.navigate("https://example.com");

            // 验证最终 URL（可能重定向也可能不重定向）
            String finalUrl = page.url();
            System.out.println("[Test] handleRedirect: final url=" + finalUrl);
            
            // 验证页面加载成功
            assertThat(page.getByText("Example Domain")).isVisible();
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 8. 本地 HTML 导航
    // ─────────────────────────────────────────────────────────────

    @Test @Order(12)
    void navigateToLocalFile() throws Exception {
        try (BrowserContext ctx = browser.newContext()) {
            // 创建临时 HTML 文件
            Path tempFile = Files.createTempFile("test", ".html");
            Files.writeString(tempFile, """
                <!DOCTYPE html>
                <html>
                <head><title>Local Test</title></head>
                <body>
                    <h1>Local Page</h1>
                    <p>This is a local file.</p>
                </body>
                </html>
                """);

            try {
                Page page = ctx.newPage();

                // 导航到本地文件
                page.navigate("file://" + tempFile.toAbsolutePath());

                assertThat(page.getByText("Local Page")).isVisible();
                assertEquals("Local Test", page.title());

                System.out.println("[Test] navigateToLocalFile: " + tempFile);
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 工具方法
    // ─────────────────────────────────────────────────────────────

    private static final List<String> COMMON_CHROME_PATHS = List.of(
            "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
            "/Applications/Chromium.app/Contents/MacOS/Chromium",
            "/usr/bin/google-chrome",
            "/usr/bin/chromium-browser",
            "C:/Program Files/Google/Chrome/Application/chrome.exe",
            "C:/Program Files (x86)/Google/Chrome/Application/chrome.exe"
    );

    private static String resolveChromePath() {
        String envChrome = System.getenv("CHROME_EXECUTABLE_PATH");
        if (envChrome != null && !envChrome.isBlank() && Files.exists(Paths.get(envChrome))) {
            return Paths.get(envChrome).toAbsolutePath().toString();
        }
        for (String candidate : COMMON_CHROME_PATHS) {
            if (Files.exists(Paths.get(candidate))) {
                return Paths.get(candidate).toAbsolutePath().toString();
            }
        }
        return null;
    }
}
