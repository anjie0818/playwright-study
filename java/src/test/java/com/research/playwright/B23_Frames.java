package com.research.playwright;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B23 - 官方文档 Frames 章节实践
 *
 * 本章核心：
 *   1. frameLocator(selector) — 通过 CSS 定位 iframe，返回 FrameLocator（链式操作）
 *   2. page.frame(name)       — 通过 name 属性获取 Frame 对象
 *   3. page.frameByUrl(pattern) — 通过 URL 匹配获取 Frame 对象
 *   4. 嵌套 iframe — frameLocator 链式定位
 *
 *   关键概念：
 *     - 每个页面有一个主框架（main frame），page.click() 等操作默认在主框架
 *     - <iframe> 标签创建子框架，需要切换到子框架才能操作内部元素
 *     - frameLocator 是链式 API，不需要显式切换框架
 *     - Frame 对象可以直接调用 fill/click 等方法
 *
 * 运行方式：
 *   mvn test -Dtest=B23_Frames
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class B23_Frames {

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
     * 主页面包含 3 个 iframe：
     * 1. login-frame (name="frame-login") — 登录表单
     * 2. nav-frame (class="nav-frame") — 导航栏
     * 3. nested-frame — 包含一个内嵌 iframe（二级嵌套）
     */
    private void setupRoutes(BrowserContext ctx) {
        // iframe 内容路由
        ctx.route("**/login.html", r -> r.fulfill(
                new Route.FulfillOptions().setContentType("text/html").setBody(
                    "<html><body>" +
                    "<input id='username' type='text' placeholder='Username'/>" +
                    "<input id='password' type='password' placeholder='Password'/>" +
                    "<button id='login-btn' onclick='document.getElementById(\"status\").textContent=\"logged in\"'>Login</button>" +
                    "<div id='status'>not logged in</div>" +
                    "</body></html>")));

        ctx.route("**/nav.html", r -> r.fulfill(
                new Route.FulfillOptions().setContentType("text/html").setBody(
                    "<html><body>" +
                    "<a id='home' href='#'>Home</a>" +
                    "<a id='settings' href='#'>Settings</a>" +
                    "<a id='profile' href='#'>Profile</a>" +
                    "</body></html>")));

        ctx.route("**/outer.html", r -> r.fulfill(
                new Route.FulfillOptions().setContentType("text/html").setBody(
                    "<html><body>" +
                    "<div id='outer-label'>Outer Frame</div>" +
                    "<iframe id='inner-iframe' src='inner.html' name='frame-inner'></iframe>" +
                    "</body></html>")));

        ctx.route("**/inner.html", r -> r.fulfill(
                new Route.FulfillOptions().setContentType("text/html").setBody(
                    "<html><body>" +
                    "<div id='inner-label'>Inner Frame</div>" +
                    "<input id='inner-input' type='text' value='hello from inner'/>" +
                    "</body></html>")));

        // 主页面
        ctx.route(PAGE_URL, r -> r.fulfill(
                new Route.FulfillOptions().setContentType("text/html").setBody(
                    "<html><body>" +
                    "<h1>Main Page</h1>" +
                    "<iframe name='frame-login' src='login.html' class='login-frame'></iframe>" +
                    "<iframe src='nav.html' class='nav-frame'></iframe>" +
                    "<iframe src='outer.html' class='outer-frame' id='nested-frame'></iframe>" +
                    "</body></html>")));
    }

    // ─────────────────────────────────────────────────────────────
    // 1. frameLocator：链式操作 iframe 内元素
    // ─────────────────────────────────────────────────────────────

    @Test
    void frameLocatorFillAndClick() {
        try (BrowserContext ctx = browser.newContext()) {
            setupRoutes(ctx);
            Page page = ctx.newPage();
            page.navigate(PAGE_URL);

            // frameLocator 链式定位 iframe 内元素，无需显式切换框架
            page.frameLocator(".login-frame")
                    .getByPlaceholder("Username").fill("admin");
            page.frameLocator(".login-frame")
                    .getByPlaceholder("Password").fill("secret");

            // 点击登录按钮
            page.frameLocator(".login-frame").locator("#login-btn").click();

            // 验证 iframe 内状态变化
            String status = page.frameLocator(".login-frame")
                    .locator("#status").textContent();
            assertEquals("logged in", status);
            System.out.println("[Test] frameLocatorFillAndClick: " + status);
        }
    }

    @Test
    void frameLocatorInteractWithNav() {
        try (BrowserContext ctx = browser.newContext()) {
            setupRoutes(ctx);
            Page page = ctx.newPage();
            page.navigate(PAGE_URL);

            // 在 nav iframe 中操作
            String homeText = page.frameLocator(".nav-frame")
                    .locator("#home").textContent();
            String settingsText = page.frameLocator(".nav-frame")
                    .locator("#settings").textContent();

            assertEquals("Home", homeText);
            assertEquals("Settings", settingsText);
            System.out.println("[Test] frameLocatorInteractWithNav: " + homeText + " / " + settingsText);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 2. page.frame(name)：按 name 属性获取 Frame 对象
    // ─────────────────────────────────────────────────────────────

    @Test
    void frameByName() {
        try (BrowserContext ctx = browser.newContext()) {
            setupRoutes(ctx);
            Page page = ctx.newPage();
            page.navigate(PAGE_URL);

            // 通过 name 属性获取 Frame 对象
            Frame loginFrame = page.frame("frame-login");
            assertNotNull(loginFrame, "Frame 'frame-login' should exist");

            // Frame 对象可以直接调用 fill/click
            loginFrame.fill("#username", "testuser");
            loginFrame.fill("#password", "pass123");
            loginFrame.click("#login-btn");

            String status = loginFrame.textContent("#status");
            assertEquals("logged in", status);

            // 验证 input 值
            String username = (String) loginFrame.evaluate("() => document.getElementById('username').value");
            assertEquals("testuser", username);
            System.out.println("[Test] frameByName: user=" + username + ", status=" + status);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 3. page.frameByUrl(pattern)：按 URL 匹配获取 Frame
    // ─────────────────────────────────────────────────────────────

    @Test
    void frameByUrl() {
        try (BrowserContext ctx = browser.newContext()) {
            setupRoutes(ctx);
            Page page = ctx.newPage();
            page.navigate(PAGE_URL);

            // 通过 URL 正则匹配获取 Frame
            Frame navFrame = page.frameByUrl(Pattern.compile(".*nav\\.html"));
            assertNotNull(navFrame, "Nav frame should exist");

            // 在 frame 中操作
            String profileText = navFrame.textContent("#profile");
            assertEquals("Profile", profileText);

            // 通过 URL 匹配 login frame
            Frame loginFrame = page.frameByUrl(Pattern.compile(".*login\\.html"));
            assertNotNull(loginFrame);
            loginFrame.fill("#username", "urluser");
            String val = (String) loginFrame.evaluate("() => document.getElementById('username').value");
            assertEquals("urluser", val);
            System.out.println("[Test] frameByUrl: profile=" + profileText + ", user=" + val);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 4. 嵌套 iframe：frameLocator 链式定位
    // ─────────────────────────────────────────────────────────────

    @Test
    void nestedFrameLocator() {
        try (BrowserContext ctx = browser.newContext()) {
            setupRoutes(ctx);
            Page page = ctx.newPage();
            page.navigate(PAGE_URL);

            // 链式定位嵌套 iframe：外层 .outer-frame → 内层 #inner-iframe
            String outerLabel = page.frameLocator(".outer-frame")
                    .locator("#outer-label").textContent();
            assertEquals("Outer Frame", outerLabel);

            String innerLabel = page.frameLocator(".outer-frame")
                    .frameLocator("#inner-iframe")
                    .locator("#inner-label").textContent();
            assertEquals("Inner Frame", innerLabel);

            // 操作嵌套 iframe 内的 input
            String innerValue = page.frameLocator(".outer-frame")
                    .frameLocator("#inner-iframe")
                    .locator("#inner-input").inputValue();
            assertEquals("hello from inner", innerValue);
            System.out.println("[Test] nestedFrameLocator: outer=" + outerLabel
                    + ", inner=" + innerLabel + ", input=" + innerValue);
        }
    }

    @Test
    void nestedFrameFillInnerInput() {
        try (BrowserContext ctx = browser.newContext()) {
            setupRoutes(ctx);
            Page page = ctx.newPage();
            page.navigate(PAGE_URL);

            // 在嵌套 iframe 的 input 中填入新值
            page.frameLocator(".outer-frame")
                    .frameLocator("#inner-iframe")
                    .locator("#inner-input").fill("modified from test");

            String value = page.frameLocator(".outer-frame")
                    .frameLocator("#inner-iframe")
                    .locator("#inner-input").inputValue();
            assertEquals("modified from test", value);
            System.out.println("[Test] nestedFrameFillInnerInput: " + value);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 5. page.frames()：列出所有 frame
    // ─────────────────────────────────────────────────────────────

    @Test
    void listAllFrames() {
        try (BrowserContext ctx = browser.newContext()) {
            setupRoutes(ctx);
            Page page = ctx.newPage();
            page.navigate(PAGE_URL);

            List<Frame> frames = page.frames();
            // 主框架 + login + nav + outer + inner = 5
            assertEquals(5, frames.size(), "Should have 5 frames (main + 3 iframes + 1 nested)");

            // 打印所有 frame 的 URL
            for (Frame f : frames) {
                System.out.println("[Test] frame: name=" + f.name() + ", url=" + f.url());
            }

            // 主框架
            Frame mainFrame = page.mainFrame();
            assertEquals(PAGE_URL + "/", mainFrame.url());
            System.out.println("[Test] listAllFrames: " + frames.size() + " frames total, main=" + mainFrame.url());
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 6. 主框架 vs 子框架操作对比
    // ─────────────────────────────────────────────────────────────

    @Test
    void mainFrameVsChildFrame() {
        try (BrowserContext ctx = browser.newContext()) {
            setupRoutes(ctx);
            Page page = ctx.newPage();
            page.navigate(PAGE_URL);

            // 主框架操作（直接用 page）
            String h1 = page.locator("h1").textContent();
            assertEquals("Main Page", h1);

            // 主框架也可以通过 mainFrame() 访问
            String h1ViaFrame = page.mainFrame().textContent("h1");
            assertEquals("Main Page", h1ViaFrame);

            // 子框架操作（需要通过 frameLocator 或 frame()）
            String loginStatus = page.frame("frame-login").textContent("#status");
            assertEquals("not logged in", loginStatus);

            System.out.println("[Test] mainFrameVsChildFrame: main=" + h1
                    + ", login status=" + loginStatus);
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
