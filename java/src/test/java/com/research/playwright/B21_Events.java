package com.research.playwright;

import com.microsoft.playwright.*;
import com.microsoft.playwright.ConsoleMessage;
import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B21 - 官方文档 Events 章节实践
 *
 * 本章核心（3 种事件模式）：
 *   1. 等待事件   — page.waitForRequest / waitForResponse / waitForPopup（lambda 内触发）
 *   2. 监听/移除  — page.onRequest / onResponse / offRequest（传统订阅模式）
 *   3. 一次性监听 — page.onceDialog（只触发一次，自动移除）
 *
 * 运行方式：
 *   mvn test -Dtest=B21_Events
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class B21_Events {

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
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    private static final String PAGE_URL = "https://test.local";

    /**
     * 测试页面：
     * - 点击按钮触发 fetch 请求
     * - 点击链接打开弹窗
     * - 点击按钮触发 prompt 对话框
     * - console.log 输出
     */
    private void setupRoutes(BrowserContext ctx) {
        String html =
            "<html><head><style>" +
            "button, a { display: block; margin: 10px; font-size: 16px; }" +
            "</style></head><body>" +
            "<button id='fetch-btn' onclick='fetch(\"/api/data\").then(r=>r.text()).then(t=>document.getElementById(\"result\").textContent=t)'>Fetch Data</button>" +
            "<div id='result'>empty</div>" +
            "<a id='popup-link' href='https://popup.local' target='_blank'>Open Popup</a>" +
            "<button id='prompt-btn' onclick='window.__promptResult = prompt(\"Enter name:\")'>Show Prompt</button>" +
            "<button id='console-btn' onclick='console.log(\"hello from page\")'>Console Log</button>" +
            "<button id='error-btn' onclick='undefinedFunc()'>Trigger Error</button>" +
            "</body></html>";

        ctx.route(PAGE_URL, r -> r.fulfill(
                new Route.FulfillOptions().setContentType("text/html").setBody(html)));

        // API 路由
        ctx.route("**/api/data", r -> r.fulfill(
                new Route.FulfillOptions().setContentType("text/plain").setBody("API response data")));

        // 图片路由（用于 URL 过滤测试）
        ctx.route("**/images/*.png", r -> r.fulfill(
                new Route.FulfillOptions().setContentType("image/png").setStatus(200)));
    }

    // ─────────────────────────────────────────────────────────────
    // 1. 等待事件：waitForRequest
    // ─────────────────────────────────────────────────────────────

    @Test
    void waitForRequestDemo() {
        try (BrowserContext ctx = browser.newContext()) {
            setupRoutes(ctx);
            Page page = ctx.newPage();
            page.navigate(PAGE_URL);

            // waitForRequest：lambda 内触发请求，返回 Request 对象
            Request request = page.waitForRequest("**/api/data", () -> {
                page.locator("#fetch-btn").click();
            });

            assertEquals("https://test.local/api/data", request.url());
            System.out.println("[Test] waitForRequest: " + request.url());
        }
    }

    @Test
    void waitForResponseDemo() {
        try (BrowserContext ctx = browser.newContext()) {
            setupRoutes(ctx);
            Page page = ctx.newPage();
            page.navigate(PAGE_URL);

            // waitForResponse：lambda 内触发请求，返回 Response 对象
            Response response = page.waitForResponse("**/api/data", () -> {
                page.locator("#fetch-btn").click();
            });

            assertEquals(200, response.status());
            assertEquals("API response data", response.text());
            System.out.println("[Test] waitForResponse: status=" + response.status()
                    + ", body=" + response.text());
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 2. 等待事件：waitForPopup
    // ─────────────────────────────────────────────────────────────

    @Test
    void waitForPopupDemo() {
        try (BrowserContext ctx = browser.newContext()) {
            setupRoutes(ctx);
            Page page = ctx.newPage();
            page.navigate(PAGE_URL);

            // waitForPopup：lambda 内触发弹窗，返回 Page 对象
            Page popup = page.waitForPopup(() -> {
                page.locator("#popup-link").click();
            });

            // popup 是新窗口，给它注入内容验证
            popup.waitForLoadState();
            assertNotNull(popup);
            System.out.println("[Test] waitForPopup: popup URL=" + popup.url());
            popup.close();
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 3. 监听/移除事件：onRequest / offRequest
    // ─────────────────────────────────────────────────────────────

    @Test
    void onRequestListener() {
        try (BrowserContext ctx = browser.newContext()) {
            setupRoutes(ctx);
            Page page = ctx.newPage();

            // 收集所有请求 URL
            List<String> requestUrls = new ArrayList<>();
            page.onRequest(request -> requestUrls.add(request.url()));

            page.navigate(PAGE_URL);
            page.locator("#fetch-btn").click();
            page.waitForFunction("() => document.getElementById('result').textContent === 'API response data'",
                    null, new Page.WaitForFunctionOptions().setTimeout(5000));

            // 验证捕获了页面请求和 API 请求
            assertTrue(requestUrls.stream().anyMatch(u -> u.contains("/api/data")),
                    "Should capture API request: " + requestUrls);
            System.out.println("[Test] onRequest: captured " + requestUrls.size()
                    + " requests: " + requestUrls);
        }
    }

    @Test
    void addAndRemoveListener() {
        try (BrowserContext ctx = browser.newContext()) {
            setupRoutes(ctx);
            Page page = ctx.newPage();

            // 添加监听器
            List<String> finishedUrls = new ArrayList<>();
            Consumer<Request> listener = request -> finishedUrls.add(request.url());
            page.onRequestFinished(listener);

            page.navigate(PAGE_URL);
            page.locator("#fetch-btn").click();
            page.waitForFunction("() => document.getElementById('result').textContent === 'API response data'",
                    null, new Page.WaitForFunctionOptions().setTimeout(5000));

            int countBeforeRemove = finishedUrls.size();
            assertTrue(countBeforeRemove > 0, "Should have captured finished requests");

            // 移除监听器
            page.offRequestFinished(listener);

            // 再次触发请求，监听器不应再被调用
            page.locator("#console-btn").click();
            page.waitForTimeout(500); // 等一下确认没有新事件

            assertEquals(countBeforeRemove, finishedUrls.size(),
                    "Listener should not be called after removal");
            System.out.println("[Test] addAndRemove: captured " + countBeforeRemove
                    + " before removal, " + finishedUrls.size() + " after (should be same)");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 4. 一次性监听：onceDialog
    // ─────────────────────────────────────────────────────────────

    @Test
    void onceDialogDemo() {
        try (BrowserContext ctx = browser.newContext()) {
            setupRoutes(ctx);
            Page page = ctx.newPage();
            page.navigate(PAGE_URL);

            // onceDialog：只监听一次，自动移除
            page.onceDialog(dialog -> {
                dialog.accept("Playwright");
                System.out.println("[Test] onceDialog: type=" + dialog.type()
                        + ", message=" + dialog.message());
            });

            // 触发 prompt 对话框
            page.locator("#prompt-btn").click();

            // 验证 prompt 返回值
            String result = (String) page.evaluate("() => window.__promptResult");
            assertEquals("Playwright", result);

            // 再次触发 prompt——onceDialog 已自动移除，这次会弹原生对话框
            // 不再触发第二次，验证 once 的语义即可
            System.out.println("[Test] onceDialog: accepted with 'Playwright', auto-removed after first call");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 5. 其他常用事件
    // ─────────────────────────────────────────────────────────────

    @Test
    void onConsoleMessage() {
        try (BrowserContext ctx = browser.newContext()) {
            setupRoutes(ctx);
            Page page = ctx.newPage();

            List<ConsoleMessage> messages = new ArrayList<>();
            page.onConsoleMessage(msg -> messages.add(msg));

            page.navigate(PAGE_URL);
            page.locator("#console-btn").click();
            page.waitForTimeout(500);

            assertTrue(messages.stream().anyMatch(m -> m.text().contains("hello from page")),
                    "Should capture console.log: " + messages);
            System.out.println("[Test] onConsoleMessage: captured " + messages.size()
                    + " messages, first=" + messages.get(0).text());
        }
    }

    @Test
    void onPageError() {
        try (BrowserContext ctx = browser.newContext()) {
            setupRoutes(ctx);
            Page page = ctx.newPage();

            List<String> errors = new ArrayList<>();
            page.onPageError(err -> errors.add(err));

            page.navigate(PAGE_URL);
            page.locator("#error-btn").click();
            page.waitForTimeout(500);

            assertTrue(errors.stream().anyMatch(e -> e.contains("undefinedFunc")),
                    "Should capture JS error: " + errors);
            System.out.println("[Test] onPageError: captured " + errors.size()
                    + " errors, first=" + errors.get(0).substring(0, Math.min(80, errors.get(0).length())));
        }
    }

    @Test
    void onPopupListener() {
        try (BrowserContext ctx = browser.newContext()) {
            setupRoutes(ctx);

            // 为弹窗 URL 也设置路由
            ctx.route("**/popup.local*", r -> r.fulfill(
                    new Route.FulfillOptions().setContentType("text/html")
                            .setBody("<html><body><h1>Popup Page</h1></body></html>")));

            Page page = ctx.newPage();
            page.navigate(PAGE_URL);

            // onPopup：监听所有弹窗（不像 waitForPopup 需要同步等待）
            List<Page> popups = new ArrayList<>();
            page.onPopup(popup -> {
                popups.add(popup);
                System.out.println("[Test] onPopup fired: " + popup.url());
            });

            page.locator("#popup-link").click();
            page.waitForTimeout(2000);

            assertTrue(popups.size() >= 1, "Should have at least 1 popup, got " + popups.size());
            popups.get(0).close();
            System.out.println("[Test] onPopupListener: captured " + popups.size() + " popup(s)");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 6. waitForRequest 配合 URL 过滤
    // ─────────────────────────────────────────────────────────────

    @Test
    void waitForRequestWithUrlFilter() {
        try (BrowserContext ctx = browser.newContext()) {
            setupRoutes(ctx);
            Page page = ctx.newPage();
            page.navigate(PAGE_URL);

            // glob 模式匹配 URL
            Request request = page.waitForRequest("**/*.png", () -> {
                // 触发一个不匹配的请求和一个匹配的请求
                page.evaluate("() => fetch('/api/data')"); // 不匹配
                page.evaluate("() => fetch('/images/logo.png')"); // 匹配
            });

            assertTrue(request.url().endsWith("logo.png"));
            System.out.println("[Test] waitForRequestWithUrlFilter: " + request.url());
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
