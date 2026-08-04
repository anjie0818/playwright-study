package com.research.playwright;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.ColorScheme;
import com.microsoft.playwright.options.Geolocation;
import com.microsoft.playwright.options.Media;
import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B19 - 官方文档 Emulation 章节实践
 *
 * 本章核心：通过 BrowserContext 配置模拟不同用户环境
 *   1.  Viewport        — 视口大小 + DeviceScaleFactor（高 DPI）
 *   2.  isMobile        — 移动端模式（meta viewport + 触摸事件）
 *   3.  Locale          — 语言区域（影响 Date/Number/Intl 格式化）
 *   4.  Timezone        — 时区模拟
 *   5.  Permissions     — 系统权限（通知、地理位置）
 *   6.  Geolocation     — 地理位置坐标
 *   7.  ColorScheme     — 深色/浅色模式
 *   8.  Media           — 媒体类型（print/screen）
 *   9.  UserAgent       — 自定义 UA
 *   10. Offline         — 离线模式
 *   11. JavaScript Disabled — 禁用 JS
 *
 * 运行方式：
 *   mvn test -Dtest=B19_Emulation
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class B19_Emulation {

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

    /**
     * 通用测试页面 HTML（用 route + navigate 注入，确保真实 origin）
     * 含 meta viewport 确保 mobile 模式下 innerWidth 正确
     */
    private static final String TEST_HTML =
        "<html><head><meta name='viewport' content='width=device-width, initial-scale=1'><style>" +
        "@media (prefers-color-scheme: dark) { body { background: #000; color: #fff; } }" +
        "@media (prefers-color-scheme: light) { body { background: #fff; color: #000; } }" +
        "@media print { body::after { content: 'PRINT-MODE'; } }" +
        "</style></head><body>" +
        "<div id='ua'></div>" +
        "<div id='lang'></div>" +
        "<div id='tz'></div>" +
        "<div id='size'></div>" +
        "<div id='js'>JS-ENABLED</div>" +
        "<script>" +
        "document.getElementById('ua').textContent = navigator.userAgent;" +
        "document.getElementById('lang').textContent = navigator.language;" +
        "document.getElementById('tz').textContent = Intl.DateTimeFormat().resolvedOptions().timeZone;" +
        "document.getElementById('size').textContent = window.innerWidth + 'x' + window.innerHeight;" +
        "</script>" +
        "</body></html>";

    private static final String PAGE_URL = "https://test.local";

    /** 用 route 拦截 + navigate，确保页面在真实 origin 下运行 */
    private void loadTestPage(BrowserContext ctx, Page page) {
        ctx.route(PAGE_URL, r -> r.fulfill(
                new Route.FulfillOptions().setContentType("text/html").setBody(TEST_HTML)));
        page.navigate(PAGE_URL);
    }

    // ─────────────────────────────────────────────────────────────
    // 1. Viewport + DeviceScaleFactor
    // ─────────────────────────────────────────────────────────────

    @Test
    void viewportSize() {
        try (BrowserContext ctx = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(1280, 1024))) {
            Page page = ctx.newPage();
            loadTestPage(ctx, page);

            String size = page.locator("#size").textContent();
            assertTrue(size.startsWith("1280x"), "Expected width 1280, got: " + size);
            System.out.println("[Test] viewportSize: " + size);
        }
    }

    @Test
    void highDpi() {
        try (BrowserContext ctx = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(2560, 1440)
                .setDeviceScaleFactor(2))) {
            Page page = ctx.newPage();
            loadTestPage(ctx, page);

            // devicePixelRatio 应为 2
            Object dpr = page.evaluate("() => window.devicePixelRatio");
            assertEquals(2.0, ((Number) dpr).doubleValue());
            System.out.println("[Test] highDpi: devicePixelRatio=" + dpr);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 2. isMobile
    // ─────────────────────────────────────────────────────────────

    @Test
    void isMobile() {
        try (BrowserContext ctx = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(375, 667)
                .setIsMobile(true)
                .setHasTouch(true))) {
            Page page = ctx.newPage();
            loadTestPage(ctx, page);

            // isMobile=true 时 ontouchstart 应存在
            boolean hasTouch = (boolean) page.evaluate("() => 'ontouchstart' in window");
            assertTrue(hasTouch, "Touch should be enabled in mobile mode");
            System.out.println("[Test] isMobile: hasTouch=" + hasTouch);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 3. Locale + Timezone
    // ─────────────────────────────────────────────────────────────

    @Test
    void localeAndTimezone() {
        try (BrowserContext ctx = browser.newContext(new Browser.NewContextOptions()
                .setLocale("de-DE")
                .setTimezoneId("Europe/Berlin"))) {
            Page page = ctx.newPage();
            loadTestPage(ctx, page);

            assertEquals("de-DE", page.locator("#lang").textContent());
            assertEquals("Europe/Berlin", page.locator("#tz").textContent());

            // 验证 locale 影响 Date 格式化
            String dateStr = (String) page.evaluate(
                "() => new Date(2026, 0, 15).toLocaleDateString()");
            assertTrue(dateStr.contains("15"), "German locale puts day first: " + dateStr);
            System.out.println("[Test] localeAndTimezone: lang=de-DE, tz=Europe/Berlin, date=" + dateStr);
        }
    }

    @Test
    void localeChinese() {
        try (BrowserContext ctx = browser.newContext(new Browser.NewContextOptions()
                .setLocale("zh-CN")
                .setTimezoneId("Asia/Shanghai"))) {
            Page page = ctx.newPage();
            loadTestPage(ctx, page);

            assertEquals("zh-CN", page.locator("#lang").textContent());
            assertEquals("Asia/Shanghai", page.locator("#tz").textContent());
            System.out.println("[Test] localeChinese: lang=zh-CN, tz=Asia/Shanghai");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 4. Permissions
    // ─────────────────────────────────────────────────────────────

    @Test
    void permissionsForNotifications() {
        try (BrowserContext ctx = browser.newContext(new Browser.NewContextOptions()
                .setPermissions(List.of("notifications")))) {
            Page page = ctx.newPage();
            loadTestPage(ctx, page);

            // Notification.permission 是同步属性，直接读取
            String notifPerm = (String) page.evaluate("() => Notification.permission");
            assertEquals("granted", notifPerm);
            System.out.println("[Test] permissionsForNotifications: " + notifPerm);
        }
    }

    @Test
    void grantAndClearPermissions() {
        // 用 route 模拟一个 origin，避免真实网络请求
        try (BrowserContext ctx = browser.newContext()) {
            ctx.route("**/perm-test", r -> r.fulfill(
                    new Route.FulfillOptions().setContentType("text/html").setBody("<html><body>perm test</body></html>")));

            // 授权 geolocation
            ctx.grantPermissions(List.of("geolocation"),
                    new BrowserContext.GrantPermissionsOptions().setOrigin("https://test.local"));

            Page page = ctx.newPage();
            page.navigate("https://test.local/perm-test");

            // Notification.permission 是同步的；geolocation 权限用 permissions API 查（也是同步 resolved）
            String notifPerm = (String) page.evaluate("() => Notification.permission");
            System.out.println("[Test] grantAndClearPermissions: notification=" + notifPerm);

            // 撤销权限
            ctx.clearPermissions();
            String afterRevoke = (String) page.evaluate("() => Notification.permission");
            System.out.println("[Test] after clearPermissions: notification=" + afterRevoke);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 5. Geolocation
    // ─────────────────────────────────────────────────────────────

    @Test
    void geolocation() {
        // geolocation 需要真实 origin（data URL 上不工作），用 route 模拟
        try (BrowserContext ctx = browser.newContext(new Browser.NewContextOptions()
                .setGeolocation(new Geolocation(41.890221, 12.492348))  // 罗马斗兽场
                .setPermissions(List.of("geolocation")))) {
            ctx.route("**/geo-test", r -> r.fulfill(
                    new Route.FulfillOptions().setContentType("text/html")
                            .setBody("<html><body><div id='result'>waiting</div>" +
                                    "<script>" +
                                    "navigator.geolocation.getCurrentPosition(" +
                                    "  pos => { document.getElementById('result').textContent = " +
                                    "    pos.coords.latitude + ',' + pos.coords.longitude; }," +
                                    "  err => { document.getElementById('result').textContent = 'error:' + err.message; }" +
                                    ");" +
                                    "</script></body></html>")));

            Page page = ctx.newPage();
            page.navigate("https://test.local/geo-test");

            // 等待 JS 回调写入结果（不用 Promise evaluate，改用 waitFor）
            page.waitForFunction("() => document.getElementById('result').textContent !== 'waiting'",
                    null,
                    new Page.WaitForFunctionOptions().setTimeout(5000));

            String result = page.locator("#result").textContent();
            System.out.println("[Test] geolocation result: " + result);
            assertTrue(result.startsWith("41.89"), "Should be Rome coords: " + result);

            // 动态更改位置（巴黎埃菲尔铁塔）
            ctx.setGeolocation(new Geolocation(48.858455, 2.294474));

            // 重新触发获取位置
            page.evaluate("() => navigator.geolocation.getCurrentPosition(" +
                    "  pos => { document.getElementById('result').textContent = " +
                    "    pos.coords.latitude + ',' + pos.coords.longitude; }," +
                    "  err => { document.getElementById('result').textContent = 'error:' + err.message; }" +
                    ")");

            page.waitForFunction("() => document.getElementById('result').textContent.startsWith('48.85')",
                    null,
                    new Page.WaitForFunctionOptions().setTimeout(5000));

            String result2 = page.locator("#result").textContent();
            System.out.println("[Test] geolocation updated to Paris: " + result2);
            assertTrue(result2.startsWith("48.85"), "Should be Paris coords: " + result2);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 6. ColorScheme（深色/浅色模式）
    // ─────────────────────────────────────────────────────────────

    @Test
    void colorSchemeDark() {
        try (BrowserContext ctx = browser.newContext(new Browser.NewContextOptions()
                .setColorScheme(ColorScheme.DARK))) {
            Page page = ctx.newPage();
            loadTestPage(ctx, page);

            // 检查 prefers-color-scheme: dark 是否生效
            boolean isDark = (boolean) page.evaluate(
                "() => window.matchMedia('(prefers-color-scheme: dark)').matches");
            assertTrue(isDark);
            System.out.println("[Test] colorSchemeDark: matchMedia dark=" + isDark);
        }
    }

    @Test
    void colorSchemeSwitchViaEmulateMedia() {
        try (BrowserContext ctx = browser.newContext(new Browser.NewContextOptions()
                .setColorScheme(ColorScheme.LIGHT))) {
            Page page = ctx.newPage();
            loadTestPage(ctx, page);

            // 初始：浅色
            boolean isLight = (boolean) page.evaluate(
                "() => window.matchMedia('(prefers-color-scheme: light)').matches");
            assertTrue(isLight);

            // 动态切换为深色
            page.emulateMedia(new Page.EmulateMediaOptions().setColorScheme(ColorScheme.DARK));
            boolean isDark = (boolean) page.evaluate(
                "() => window.matchMedia('(prefers-color-scheme: dark)').matches");
            assertTrue(isDark);
            System.out.println("[Test] colorSchemeSwitch: light → dark via emulateMedia");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 7. Media（媒体类型模拟，如 print）
    // ─────────────────────────────────────────────────────────────

    @Test
    void mediaPrint() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadTestPage(ctx, page);

            // 初始：screen 模式
            boolean isScreen = (boolean) page.evaluate(
                "() => window.matchMedia('screen').matches");
            assertTrue(isScreen);

            // 切换到 print 模式
            page.emulateMedia(new Page.EmulateMediaOptions().setMedia(Media.PRINT));
            boolean isPrint = (boolean) page.evaluate(
                "() => window.matchMedia('print').matches");
            assertTrue(isPrint);
            System.out.println("[Test] mediaPrint: screen → print via emulateMedia");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 8. UserAgent
    // ─────────────────────────────────────────────────────────────

    @Test
    void customUserAgent() {
        try (BrowserContext ctx = browser.newContext(new Browser.NewContextOptions()
                .setUserAgent("My Test Agent / 1.0"))) {
            Page page = ctx.newPage();
            loadTestPage(ctx, page);

            String ua = page.locator("#ua").textContent();
            assertEquals("My Test Agent / 1.0", ua);
            System.out.println("[Test] customUserAgent: " + ua);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 9. Offline
    // ─────────────────────────────────────────────────────────────

    @Test
    void offlineMode() {
        try (BrowserContext ctx = browser.newContext(new Browser.NewContextOptions()
                .setOffline(true))) {
            // 不设 route，offline=true 时浏览器无法发起任何请求
            Page page = ctx.newPage();

            // 离线模式下导航会失败
            assertThrows(PlaywrightException.class, () -> {
                page.navigate("http://test.local/offline",
                        new Page.NavigateOptions().setTimeout(3000));
            });
            System.out.println("[Test] offlineMode: navigation failed as expected (offline)");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 10. JavaScript Disabled
    // ─────────────────────────────────────────────────────────────

    @Test
    void javaScriptDisabled() {
        try (BrowserContext ctx = browser.newContext(new Browser.NewContextOptions()
                .setJavaScriptEnabled(false))) {
            Page page = ctx.newPage();
            loadTestPage(ctx, page);

            // JS 禁用后，script 标签不执行，#js 仍为默认文本
            String jsDiv = page.locator("#js").textContent();
            assertEquals("JS-ENABLED", jsDiv); // 静态 HTML 内容仍在

            // 但 #ua 等由 JS 填充的 div 为空
            String uaDiv = page.locator("#ua").textContent();
            assertTrue(uaDiv == null || uaDiv.isEmpty(), "#ua should be empty (JS not executed)");
            System.out.println("[Test] javaScriptDisabled: #ua is empty (JS not executed)");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 11. 内置设备预设（Playwright.DeviceDescriptor）
    // ─────────────────────────────────────────────────────────────

    /**
     * Playwright Java 没有 Node.js 版的 playwright.devices() 内置预设，
     * 需要手动配置设备参数。这里模拟 iPhone 13 的关键参数。
     */
    @Test
    void builtInDevicePreset() {
        // iPhone 13 参数（手动配置）
        Browser.NewContextOptions opts = new Browser.NewContextOptions()
                .setViewportSize(390, 844)
                .setDeviceScaleFactor(3)
                .setIsMobile(true)
                .setHasTouch(true)
                .setUserAgent("Mozilla/5.0 (iPhone; CPU iPhone OS 15_0 like Mac OS X) " +
                        "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.0 Mobile/15E148 Safari/604.1");

        try (BrowserContext ctx = browser.newContext(opts)) {
            Page page = ctx.newPage();
            loadTestPage(ctx, page);

            String ua = page.locator("#ua").textContent();
            assertTrue(ua.contains("iPhone"), "UA should contain iPhone: " + ua);

            String size = page.locator("#size").textContent();
            assertTrue(size.startsWith("390x"), "Viewport width should be 390: " + size);

            boolean hasTouch = (boolean) page.evaluate("() => 'ontouchstart' in window");
            assertTrue(hasTouch);

            System.out.println("[Test] builtInDevicePreset: iPhone 13, UA="
                    + ua.substring(0, 50) + "..., size=" + size);
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
