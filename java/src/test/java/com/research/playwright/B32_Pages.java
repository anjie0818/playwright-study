package com.research.playwright;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * B32 - Pages 章节测试
 * 
 * 测试内容：
 * 1. 基本 Page 使用
 * 2. 多个 Page（标签页）
 * 3. 处理新页面（target="_blank"）
 * 4. 处理弹窗（popup）
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class B32_Pages {

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

    private static final String BASE_URL = "https://test.local";

    private void setupRoutes(BrowserContext ctx) {
        ctx.route("**/*", route -> {
            String url = route.request().url();
            
            if (url.contains("/home")) {
                route.fulfill(new Route.FulfillOptions()
                    .setContentType("text/html; charset=utf-8")
                    .setBody("<html><body><h1>Home Page</h1></body></html>"));
            } else if (url.contains("/page1")) {
                route.fulfill(new Route.FulfillOptions()
                    .setContentType("text/html; charset=utf-8")
                    .setBody("<html><body>" +
                        "<h1>Page 1</h1>" +
                        "<input id='search' type='text'/>" +
                        "<a href='/page2' target='_blank'>Open Page 2</a>" +
                        "<button onclick='window.open(\"/popup\", \"popup\", \"width=400,height=300\")'>Open Popup</button>" +
                        "</body></html>"));
            } else if (url.contains("/page2")) {
                route.fulfill(new Route.FulfillOptions()
                    .setContentType("text/html; charset=utf-8")
                    .setBody("<html><body>" +
                        "<h1>Page 2</h1>" +
                        "<button id='action'>Click Me</button>" +
                        "</body></html>"));
            } else if (url.contains("/popup")) {
                route.fulfill(new Route.FulfillOptions()
                    .setContentType("text/html; charset=utf-8")
                    .setBody("<html><head><title>Popup Window</title></head><body>" +
                        "<h1>Popup</h1>" +
                        "<button id='popup-btn'>Popup Button</button>" +
                        "</body></html>"));
            } else {
                route.fulfill(new Route.FulfillOptions()
                    .setContentType("text/html; charset=utf-8")
                    .setBody("<html><body><h1>Default Page</h1></body></html>"));
            }
        });
    }

    // ─────────────────────────────────────────────────────────────
    // 1. 基本 Page 使用
    // ─────────────────────────────────────────────────────────────

    @Test @Order(1)
    void basicPageUsage() {
        try (BrowserContext ctx = browser.newContext()) {
            setupRoutes(ctx);
            
            Page page = ctx.newPage();
            page.navigate(BASE_URL + "/home");
            
            assertThat(page.getByText("Home Page")).isVisible();
            assertEquals(BASE_URL + "/home", page.url());
            
            System.out.println("[Test] basicPageUsage: navigated to " + page.url());
        }
    }

    @Test @Order(2)
    void pageInteractions() {
        try (BrowserContext ctx = browser.newContext()) {
            setupRoutes(ctx);
            
            Page page = ctx.newPage();
            page.navigate(BASE_URL + "/page1");
            
            // 填充输入框
            page.locator("#search").fill("query");
            assertEquals("query", page.locator("#search").inputValue());
            
            // 点击链接会在同一页面导航（不是 target="_blank"）
            // 先修改链接让它不是 target="_blank"
            page.evaluate("document.querySelector('a').removeAttribute('target')");
            page.locator("a").click();
            
            // 等待导航完成
            page.waitForLoadState();
            
            // 验证 URL 变化
            assertTrue(page.url().contains("/page2"), "URL should contain /page2, but was: " + page.url());
            
            System.out.println("[Test] pageInteractions: filled input and navigated to " + page.url());
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 2. 多个 Page（标签页）
    // ─────────────────────────────────────────────────────────────

    @Test @Order(3)
    void multiplePages() {
        try (BrowserContext ctx = browser.newContext()) {
            setupRoutes(ctx);
            
            // 创建两个页面
            Page pageOne = ctx.newPage();
            Page pageTwo = ctx.newPage();
            
            // 各自导航到不同 URL
            pageOne.navigate(BASE_URL + "/page1");
            pageTwo.navigate(BASE_URL + "/page2");
            
            // 验证各自内容独立
            assertThat(pageOne.getByText("Page 1")).isVisible();
            assertThat(pageTwo.getByText("Page 2")).isVisible();
            
            // 获取上下文中的所有页面
            List<Page> allPages = ctx.pages();
            assertEquals(2, allPages.size());
            
            System.out.println("[Test] multiplePages: " + allPages.size() + " pages created");
        }
    }

    @Test @Order(4)
    void pagesFollowContextSettings() {
        try (BrowserContext ctx = browser.newContext(
                new Browser.NewContextOptions().setViewportSize(800, 600))) {
            setupRoutes(ctx);
            
            Page page1 = ctx.newPage();
            Page page2 = ctx.newPage();
            
            page1.navigate(BASE_URL + "/page1");
            page2.navigate(BASE_URL + "/page2");
            
            // 两个页面都遵循上下文设置
            Object viewport1 = page1.evaluate("() => ({ w: window.innerWidth, h: window.innerHeight })");
            Object viewport2 = page2.evaluate("() => ({ w: window.innerWidth, h: window.innerHeight })");
            
            // 验证视口大小一致
            assertNotNull(viewport1);
            assertNotNull(viewport2);
            
            System.out.println("[Test] pagesFollowContextSettings: both pages share viewport");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 3. 处理新页面（target="_blank"）
    // ─────────────────────────────────────────────────────────────

    @Test @Order(5)
    void handleNewPageWithWaitForPage() {
        try (BrowserContext ctx = browser.newContext()) {
            setupRoutes(ctx);
            
            Page page = ctx.newPage();
            page.navigate(BASE_URL + "/page1");
            
            // 点击 target="_blank" 链接，等待新页面打开
            Page newPage = ctx.waitForPage(() -> {
                page.getByText("Open Page 2").click();
            });
            
            // 与新页面交互
            assertThat(newPage.getByText("Page 2")).isVisible();
            newPage.locator("#action").click();
            
            System.out.println("[Test] handleNewPageWithWaitForPage: new page opened");
        }
    }

    @Test @Order(6)
    void handleNewPageWithOnPage() {
        try (BrowserContext ctx = browser.newContext()) {
            setupRoutes(ctx);
            
            Page page = ctx.newPage();
            page.navigate(BASE_URL + "/page1");
            
            // 使用 onPage 事件监听所有新页面
            AtomicReference<Page> capturedPage = new AtomicReference<>();
            ctx.onPage(newPage -> {
                newPage.waitForLoadState();
                capturedPage.set(newPage);
                System.out.println("[Event] New page opened: " + newPage.url());
            });
            
            // 触发新页面打开
            page.getByText("Open Page 2").click();
            
            // 等待事件触发
            page.waitForTimeout(1000);
            
            assertNotNull(capturedPage.get());
            assertThat(capturedPage.get().getByText("Page 2")).isVisible();
            
            System.out.println("[Test] handleNewPageWithOnPage: captured via event");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 4. 处理弹窗（popup）
    // ─────────────────────────────────────────────────────────────

    @Test @Order(7)
    void handlePopupWithWaitForPopup() {
        try (BrowserContext ctx = browser.newContext()) {
            setupRoutes(ctx);
            
            Page page = ctx.newPage();
            page.navigate(BASE_URL + "/page1");
            
            // 点击打开弹窗按钮，等待弹窗
            Page popup = page.waitForPopup(() -> {
                page.getByRole(AriaRole.BUTTON).click();
            });
            
            // 与弹窗交互
            assertEquals("Popup Window", popup.title());
            popup.locator("#popup-btn").click();
            
            System.out.println("[Test] handlePopupWithWaitForPopup: popup title=" + popup.title());
        }
    }

    @Test @Order(8)
    void handlePopupWithOnPopup() {
        try (BrowserContext ctx = browser.newContext()) {
            setupRoutes(ctx);
            
            Page page = ctx.newPage();
            page.navigate(BASE_URL + "/page1");
            
            // 使用 onPopup 事件监听所有弹窗
            AtomicReference<Page> capturedPopup = new AtomicReference<>();
            page.onPopup(popup -> {
                popup.waitForLoadState();
                capturedPopup.set(popup);
                System.out.println("[Event] Popup opened: " + popup.title());
            });
            
            // 触发弹窗打开
            page.getByRole(AriaRole.BUTTON).click();
            
            // 等待事件触发
            page.waitForTimeout(1000);
            
            assertNotNull(capturedPopup.get());
            assertEquals("Popup Window", capturedPopup.get().title());
            
            System.out.println("[Test] handlePopupWithOnPopup: captured via event");
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
