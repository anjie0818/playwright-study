package com.research.playwright;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * B13 - 官方文档 Authentication 章节实践
 *
 * 本章核心：
 *   Playwright 在隔离的 BrowserContext 中运行，认证状态不会自动跨测试共享。
 *   本章介绍三种认证模式：
 *
 *   1. 每次测试前重新登录（最简单，但最慢）
 *   2. storageState 持久化：登录一次，保存 cookies/localStorage 到文件，
 *      后续测试直接加载已认证状态（最常用）
 *   3. Session Storage 手动保存与恢复（用 evaluate + addInitScript 处理）
 *
 * 关键说明：
 *   Chromium 对 about:blank（page.setContent 默认 origin）禁用 localStorage/sessionStorage。
 *   解决方案：用 page.route() 拦截一个 http:// 假 URL，返回 HTML 内容。
 *   这样页面有合法 http origin，Web Storage API 完全可用。
 *
 *   HTML 页面放在 src/test/resources/pages/ 下：
 *     - auth-login.html
 *     - auth-protected.html
 *
 * 运行方式：
 *   mvn test -Dtest=B13_Authentication
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class B13_Authentication {

    private static final Path AUTH_DIR   = Paths.get("playwright/.auth");
    private static final Path STATE_FILE = AUTH_DIR.resolve("user.json");

    private static final String LOGIN_URL     = "http://test.local/login";
    private static final String PROTECTED_URL = "http://test.local/dashboard";

    private Playwright playwright;
    private Browser browser;

    @BeforeAll
    void beforeAll() throws Exception {
        Map<String, String> env = new HashMap<>(System.getenv());
        env.put("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1");

        playwright = Playwright.create(new Playwright.CreateOptions().setEnv(env));

        String chromePath = resolveChromePath();
        BrowserType.LaunchOptions opts = new BrowserType.LaunchOptions().setHeadless(true);
        if (chromePath != null) opts.setExecutablePath(Paths.get(chromePath));
        browser = playwright.chromium().launch(opts);

        Files.createDirectories(AUTH_DIR);
        System.out.println("[Setup] Browser launched. Auth dir: " + AUTH_DIR.toAbsolutePath());
    }

    @AfterAll
    void afterAll() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    /**
     * 从 classpath 读取 HTML 页面内容。
     * 资源文件路径：src/test/resources/pages/auth-*.html
     */
    private static String readHtml(String resourceName) {
        try (var is = B13_Authentication.class.getClassLoader().getResourceAsStream("pages/" + resourceName)) {
            if (is == null) {
                throw new RuntimeException("Resource not found: pages/" + resourceName);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read resource: pages/" + resourceName, e);
        }
    }

    /**
     * 在 context 中注册路由拦截，使 http://test.local/* 返回 HTML 内容。
     */
    private static void setupRoutes(BrowserContext ctx) {
        String loginHtml = readHtml("auth-login.html");
        String protectedHtml = readHtml("auth-protected.html");

        ctx.route(LOGIN_URL, route ->
            route.fulfill(new Route.FulfillOptions()
                .setContentType("text/html")
                .setBody(loginHtml))
        );
        ctx.route(PROTECTED_URL, route ->
            route.fulfill(new Route.FulfillOptions()
                .setContentType("text/html")
                .setBody(protectedHtml))
        );
    }

    // ─────────────────────────────────────────────────────────────
    // 1. 每次测试前重新登录
    // ─────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    void loginBeforeEachTest() {
        try (BrowserContext ctx = browser.newContext()) {
            setupRoutes(ctx);
            Page page = ctx.newPage();
            page.navigate(LOGIN_URL);

            page.locator("#username").fill("admin");
            page.locator("#password").fill("secret");
            page.locator("#login-btn").click();

            assertThat(page.locator("#msg")).hasText("Welcome, admin!");

            String token = (String) page.evaluate("localStorage.getItem('token')");
            org.junit.jupiter.api.Assertions.assertEquals("tok-abc123", token);

            System.out.println("[Test] loginBeforeEachTest passed. token=" + token);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 2a. 登录并保存 storageState 到文件
    // ─────────────────────────────────────────────────────────────

    @Test
    @Order(2)
    void saveStorageState() throws Exception {
        try (BrowserContext ctx = browser.newContext()) {
            setupRoutes(ctx);
            Page page = ctx.newPage();
            page.navigate(LOGIN_URL);

            page.locator("#username").fill("admin");
            page.locator("#password").fill("secret");
            page.locator("#login-btn").click();
            assertThat(page.locator("#msg")).hasText("Welcome, admin!");

            ctx.storageState(new BrowserContext.StorageStateOptions().setPath(STATE_FILE));

            org.junit.jupiter.api.Assertions.assertTrue(
                Files.exists(STATE_FILE), "State file should exist"
            );

            System.out.println("[Test] saveStorageState passed. File size=" + Files.size(STATE_FILE) + "B");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 2b. 加载 storageState，跳过登录直接访问受保护资源
    // ─────────────────────────────────────────────────────────────

    @Test
    @Order(3)
    void reuseStorageState() {
        org.junit.jupiter.api.Assumptions.assumeTrue(
            Files.exists(STATE_FILE), "Run saveStorageState first"
        );

        try (BrowserContext ctx = browser.newContext(
                new Browser.NewContextOptions().setStorageStatePath(STATE_FILE))) {
            setupRoutes(ctx);
            Page page = ctx.newPage();

            page.navigate(PROTECTED_URL);

            assertThat(page.locator("#content")).hasText("Dashboard: secret data");

            String token = (String) page.evaluate("localStorage.getItem('token')");
            org.junit.jupiter.api.Assertions.assertEquals("tok-abc123", token);

            System.out.println("[Test] reuseStorageState passed. Protected content visible without login.");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 3. Session Storage 手动保存与恢复
    // ─────────────────────────────────────────────────────────────

    @Test
    @Order(4)
    void sessionStorageSaveAndRestore() {
        String capturedSession;

        try (BrowserContext sourceCtx = browser.newContext()) {
            setupRoutes(sourceCtx);
            Page page = sourceCtx.newPage();
            page.navigate(LOGIN_URL);

            page.evaluate("sessionStorage.setItem('role', 'admin')");
            page.evaluate("sessionStorage.setItem('expire', '2099-12-31')");

            capturedSession = (String) page.evaluate(
                "var r={}; for(var i=0;i<sessionStorage.length;i++){" +
                "  var k=sessionStorage.key(i); r[k]=sessionStorage.getItem(k);" +
                "} JSON.stringify(r);"
            );
            System.out.println("[Test] Captured sessionStorage: " + capturedSession);
        }

        try (BrowserContext targetCtx = browser.newContext()) {
            setupRoutes(targetCtx);

            // 用 addInitScript 在每次页面加载前注入 sessionStorage
            targetCtx.addInitScript(capturedSession);

            Page page = targetCtx.newPage();
            page.navigate(PROTECTED_URL);

            // 验证 sessionStorage 已恢复
            String restoredRole = (String) page.evaluate("sessionStorage.getItem('role')");
            org.junit.jupiter.api.Assertions.assertEquals("admin", restoredRole,
                    "sessionStorage should be restored via addInitScript");
            System.out.println("[Test] sessionStorageSaveAndRestore passed. role=" + restoredRole);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 工具方法
    // ─────────────────────────────────────────────────────────────

    private static String resolveChromePath() {
        String[] candidates = {
            "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
            "/usr/bin/google-chrome",
            "/usr/bin/chromium-browser",
            "C:/Program Files/Google/Chrome/Application/chrome.exe",
        };
        for (String p : candidates) {
            if (new java.io.File(p).exists()) return p;
        }
        return null;
    }
}

