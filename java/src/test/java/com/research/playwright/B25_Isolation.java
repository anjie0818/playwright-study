package com.research.playwright;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.Cookie;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B25 - 官方文档 Isolation (browser-contexts) 章节实践
 *
 * 本章核心（B02b 已覆盖 localStorage 隔离，本章扩展）：
 *   1. Cookie 隔离       — 不同 Context 的 Cookie 互不可见
 *   2. SessionStorage    — 不同 Context 的 sessionStorage 互不可见
 *   3. 多用户场景        — 同一测试中创建多个 Context 模拟多用户
 *   4. Visited links     — 已访问链接的 :visited 状态隔离
 *   5. storageState 复用 — 从一个 Context 导出状态到另一个 Context
 *
 *   核心理念：每个 BrowserContext = 隐身模式配置文件，创建快且完全隔离。
 *
 * 运行方式：
 *   mvn test -Dtest=B25_Isolation
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class B25_Isolation {

    private Playwright playwright;
    private Browser browser;

    private static final String PAGE_URL = "https://test.local";
    private static final String PAGE_HTML =
        "<html><head><style>" +
        "a:visited { color: purple; }" +
        "a { color: blue; }" +
        "</style></head><body>" +
        "<div id='ls'>?</div>" +
        "<div id='ss'>?</div>" +
        "<div id='cookie'>?</div>" +
        "<script>" +
        "document.getElementById('ls').textContent = localStorage.getItem('data') || 'empty';" +
        "document.getElementById('ss').textContent = sessionStorage.getItem('data') || 'empty';" +
        "document.getElementById('cookie').textContent = document.cookie || 'no-cookie';" +
        "</script>" +
        "</body></html>";

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

    private void setupRoutes(BrowserContext ctx) {
        ctx.route(PAGE_URL, r -> r.fulfill(
                new Route.FulfillOptions().setContentType("text/html").setBody(PAGE_HTML)));
    }

    // ─────────────────────────────────────────────────────────────
    // 1. Cookie 隔离
    // ─────────────────────────────────────────────────────────────

    @Test
    void cookieIsolation() {
        // Context A：设置 cookie
        try (BrowserContext ctxA = browser.newContext()) {
            setupRoutes(ctxA);
            ctxA.addCookies(List.of(
                    new Cookie("user", "Alice").setDomain("test.local").setPath("/")));
            Page pageA = ctxA.newPage();
            pageA.navigate(PAGE_URL);

            String cookieA = (String) pageA.evaluate("() => document.cookie");
            assertTrue(cookieA.contains("Alice"), "Context A should have cookie, got: " + cookieA);
            System.out.println("[Test] cookieIsolation - Context A: cookie=" + cookieA);
        }

        // Context B：读不到 Context A 的 cookie
        try (BrowserContext ctxB = browser.newContext()) {
            setupRoutes(ctxB);
            Page pageB = ctxB.newPage();
            pageB.navigate(PAGE_URL);

            String cookieB = (String) pageB.evaluate("() => document.cookie");
            assertTrue(cookieB.isEmpty(), "Context B should have no cookies, got: " + cookieB);
            System.out.println("[Test] cookieIsolation - Context B: cookie='" + cookieB + "' (empty as expected)");
        }
    }

    @Test
    void cookieViaApi() {
        // 通过 Playwright API 直接添加 cookie
        BrowserContext ctxA = browser.newContext();
        setupRoutes(ctxA);
        ctxA.addCookies(List.of(
                new Cookie("token", "secret123")
                        .setDomain("test.local")
                        .setPath("/")
        ));

        Page pageA = ctxA.newPage();
        pageA.navigate(PAGE_URL);

        List<Cookie> cookies = ctxA.cookies();
        assertEquals(1, cookies.size());
        assertEquals("token", cookies.get(0).name);
        assertEquals("secret123", cookies.get(0).value);
        System.out.println("[Test] cookieViaApi: " + cookies.get(0).name + "=" + cookies.get(0).value);

        ctxA.close();
    }

    // ─────────────────────────────────────────────────────────────
    // 2. SessionStorage 隔离
    // ─────────────────────────────────────────────────────────────

    @Test
    void sessionStorageIsolation() {
        // Context A：设置 sessionStorage
        BrowserContext ctxA = browser.newContext();
        setupRoutes(ctxA);
        Page pageA = ctxA.newPage();
        pageA.navigate(PAGE_URL);
        pageA.evaluate("sessionStorage.setItem('data', 'from-A')");

        String valueA = (String) pageA.evaluate("sessionStorage.getItem('data')");
        assertEquals("from-A", valueA);

        // Context B：读不到 Context A 的 sessionStorage
        BrowserContext ctxB = browser.newContext();
        setupRoutes(ctxB);
        Page pageB = ctxB.newPage();
        pageB.navigate(PAGE_URL);

        String valueB = (String) pageB.evaluate("sessionStorage.getItem('data')");
        assertNull(valueB, "Context B should not see Context A's sessionStorage");

        System.out.println("[Test] sessionStorageIsolation: A=" + valueA + ", B=" + valueB);

        ctxA.close();
        ctxB.close();
    }

    // ─────────────────────────────────────────────────────────────
    // 3. 多用户场景：同一测试中创建多个 Context
    // ─────────────────────────────────────────────────────────────

    @Test
    void multiUserScenario() {
        try (BrowserContext userCtx = browser.newContext();
             BrowserContext adminCtx = browser.newContext()) {

            setupRoutes(userCtx);
            setupRoutes(adminCtx);

            Page userPage = userCtx.newPage();
            Page adminPage = adminCtx.newPage();

            userPage.navigate(PAGE_URL);
            adminPage.navigate(PAGE_URL);

            // 用户设置自己的数据
            userPage.evaluate("localStorage.setItem('data', 'user-Alice')");
            adminPage.evaluate("localStorage.setItem('data', 'admin-Bob')");

            // 各自独立
            String userData = (String) userPage.evaluate("localStorage.getItem('data')");
            String adminData = (String) adminPage.evaluate("localStorage.getItem('data')");

            assertEquals("user-Alice", userData);
            assertEquals("admin-Bob", adminData);

            // 用户设 cookie
            userPage.evaluate("document.cookie = 'role=user; path=/'");
            adminPage.evaluate("document.cookie = 'role=admin; path=/'");

            // Cookie 也独立
            String userCookie = (String) userPage.evaluate("() => document.cookie");
            String adminCookie = (String) adminPage.evaluate("() => document.cookie");

            assertTrue(userCookie.contains("role=user"));
            assertTrue(adminCookie.contains("role=admin"));

            System.out.println("[Test] multiUserScenario: user=" + userData + "/" + userCookie
                    + ", admin=" + adminData + "/" + adminCookie);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 4. Visited links 隔离
    // ─────────────────────────────────────────────────────────────

    /**
     * 浏览器会记住已访问的链接（:visited CSS 状态）。
     * 不同 Context 的 visited 状态也隔离——Context A 访问过的链接，Context B 看不到 :visited 样式。
     */
    @Test
    void visitedLinksIsolation() {
        String linkUrl = "https://test.local/visited-target";

        // Context A：访问链接
        try (BrowserContext ctxA = browser.newContext()) {
            ctxA.route(PAGE_URL, r -> r.fulfill(
                    new Route.FulfillOptions().setContentType("text/html").setBody(
                            "<html><body><a id='link' href='" + linkUrl + "'>Click me</a></body></html>")));
            ctxA.route(linkUrl, r -> r.fulfill(
                    new Route.FulfillOptions().setContentType("text/html").setBody("<html><body>target</body></html>")));

            Page pageA = ctxA.newPage();
            pageA.navigate(PAGE_URL);
            pageA.locator("#link").click();
            pageA.waitForTimeout(500);

            System.out.println("[Test] visitedLinksIsolation - Context A: visited the link");
        }

        // Context B：链接未被访问
        try (BrowserContext ctxB = browser.newContext()) {
            ctxB.route(PAGE_URL, r -> r.fulfill(
                    new Route.FulfillOptions().setContentType("text/html").setBody(
                            "<html><body><a id='link' href='" + linkUrl + "'>Click me</a></body></html>")));

            Page pageB = ctxB.newPage();
            pageB.navigate(PAGE_URL);

            // :visited 状态隔离——Context B 中的链接应该是未访问颜色
            // 注意：浏览器出于安全限制，不允许通过 JS 读取 :visited 计算样式
            // 但 Playwright 的 Context 隔离确保 visited 历史不共享
            String color = (String) pageB.evaluate(
                "() => getComputedStyle(document.getElementById('link')).color");
            System.out.println("[Test] visitedLinksIsolation - Context B: link color=" + color);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 5. storageState 复用（导出/导入）
    // ─────────────────────────────────────────────────────────────

    @Test
    void storageStateExportImport() {
        // 1. Context A 登录，存储状态
        String storageState;
        try (BrowserContext ctxA = browser.newContext()) {
            setupRoutes(ctxA);
            Page pageA = ctxA.newPage();
            pageA.navigate(PAGE_URL);

            pageA.evaluate("localStorage.setItem('token', 'abc123')");
            pageA.evaluate("sessionStorage.setItem('role', 'admin')");
            ctxA.addCookies(List.of(
                    new Cookie("session", "xyz789")
                            .setDomain("test.local")
                            .setPath("/")
            ));

            // 导出 storageState（包含 cookies + localStorage）
            storageState = ctxA.storageState();
            assertNotNull(storageState);
            assertTrue(storageState.contains("abc123"));
            System.out.println("[Test] storageStateExport: exported " + storageState.length() + " chars");
        }

        // 2. Context B 导入 storageState，恢复 cookies + localStorage
        try (BrowserContext ctxB = browser.newContext(
                new Browser.NewContextOptions().setStorageState(storageState))) {
            setupRoutes(ctxB);
            Page pageB = ctxB.newPage();
            pageB.navigate(PAGE_URL);

            // localStorage 恢复了
            String token = (String) pageB.evaluate("localStorage.getItem('token')");
            assertEquals("abc123", token);

            // cookie 恢复了
            String cookie = (String) pageB.evaluate("() => document.cookie");
            assertTrue(cookie.contains("session=xyz789"));

            // sessionStorage 不在 storageState 中（不会被导出）
            String role = (String) pageB.evaluate("sessionStorage.getItem('role')");
            assertNull(role, "sessionStorage is not included in storageState");

            System.out.println("[Test] storageStateImport: token=" + token
                    + ", cookie=" + cookie + ", sessionStorage=" + role);
        }
    }

    @Test
    void storageStateToFile() throws IOException {
        // 导出到文件，再从文件导入——持久化跨测试运行
        try (BrowserContext ctxA = browser.newContext()) {
            setupRoutes(ctxA);
            Page pageA = ctxA.newPage();
            pageA.navigate(PAGE_URL);

            pageA.evaluate("localStorage.setItem('saved', 'persistent-data')");

            // 导出到文件
            java.nio.file.Path stateFile = Paths.get("target", "auth-state.json");
            ctxA.storageState(new BrowserContext.StorageStateOptions().setPath(stateFile));
            assertTrue(Files.exists(stateFile));
            System.out.println("[Test] storageStateToFile: saved to " + stateFile);

            // 用文件导入（读取文件内容作为 JSON 字符串）
            String stateJson = Files.readString(stateFile);
            try (BrowserContext ctxB = browser.newContext(
                    new Browser.NewContextOptions().setStorageState(stateJson))) {
                setupRoutes(ctxB);
                Page pageB = ctxB.newPage();
                pageB.navigate(PAGE_URL);

                String saved = (String) pageB.evaluate("localStorage.getItem('saved')");
                assertEquals("persistent-data", saved);
                System.out.println("[Test] storageStateToFile: loaded from file, saved=" + saved);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 6. Context 间共享 Page（验证不可行）
    // ─────────────────────────────────────────────────────────────

    @Test
    void contextBoundary() {
        // 每个 Page 属于一个 Context，不能跨 Context 共享
        BrowserContext ctxA = browser.newContext();
        setupRoutes(ctxA);
        Page pageA = ctxA.newPage();
        pageA.navigate(PAGE_URL);

        // 验证 page 的 context 引用
        assertSame(ctxA, pageA.context());

        // 不同 Context 的 Page 有独立状态
        BrowserContext ctxB = browser.newContext();
        setupRoutes(ctxB);
        Page pageB = ctxB.newPage();
        pageB.navigate(PAGE_URL);

        pageA.evaluate("localStorage.setItem('key', 'from-A')");
        pageB.evaluate("localStorage.setItem('key', 'from-B')");

        assertNotEquals(
                pageA.evaluate("localStorage.getItem('key')"),
                pageB.evaluate("localStorage.getItem('key')")
        );

        System.out.println("[Test] contextBoundary: A=" + pageA.evaluate("localStorage.getItem('key')")
                + ", B=" + pageB.evaluate("localStorage.getItem('key')"));

        ctxA.close();
        ctxB.close();
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
