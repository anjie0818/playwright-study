package com.research.playwright;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * B15 - 官方文档 Clock 章节实践
 *
 * 本章核心：
 *   page.clock() 提供对浏览器时间的完全控制，覆盖 Date、setTimeout、setInterval 等原生 API。
 *   测试时间相关功能无需真实等待，可瞬间"穿越"到任意时刻。
 *
 *   覆盖知识点：
 *     1. setFixedTime  → 固定 Date.now() 返回值，定时器仍正常运行
 *     2. install + pauseAt + fastForward → 完整时间控制流程
 *     3. install + fastForward → 测试超时/非活跃登出等场景
 *     4. pauseAt + runFor → 手动精确步进（逐毫秒触发定时器）
 *
 * 注意：本示例用 page.route() + setContent 模拟页面，不依赖真实服务器。
 *
 * 运行方式：
 *   mvn test -Dtest=B15_Clock
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class B15_Clock {

    private static final SimpleDateFormat FMT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    private Playwright playwright;
    private Browser browser;

    // 模拟"显示当前时间"页面：每秒用 setInterval 更新显示
    private static final String CLOCK_PAGE_HTML =
        "<html><body>" +
        "<div data-testid='current-time'>--</div>" +
        "<script>" +
        "const render = () => {" +
        "  document.querySelector('[data-testid=current-time]').textContent" +
        "    = new Date().toLocaleString('en-US');" +
        "};" +
        "render();" +
        "setInterval(render, 1000);" +
        "</script>" +
        "</body></html>";

    // 模拟"非活跃倒计时登出"页面：5 分钟无操作后登出
    private static final String INACTIVITY_PAGE_HTML =
        "<html><body>" +
        "<div data-testid='status'>Active</div>" +
        "<button id='btn'>Keep alive</button>" +
        "<script>" +
        "const END = Date.now() + 5 * 60 * 1000;" +
        "const tick = () => {" +
        "  const left = Math.round((END - Date.now()) / 1000);" +
        "  if (left <= 0) {" +
        "    document.querySelector('[data-testid=status]').textContent = 'Logged out due to inactivity.';" +
        "  } else {" +
        "    document.querySelector('[data-testid=status]').textContent = 'You will be logged out in ' + left + ' seconds.';" +
        "    setTimeout(tick, 1000);" +
        "  }" +
        "};" +
        "tick();" +
        "</script>" +
        "</body></html>";

    private static final String CLOCK_URL      = "http://test.local/clock";
    private static final String INACTIVITY_URL = "http://test.local/inactivity";

    @BeforeAll
    void beforeAll() {
        Map<String, String> env = new HashMap<>(System.getenv());
        env.put("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1");
        playwright = Playwright.create(new Playwright.CreateOptions().setEnv(env));

        String chromePath = resolveChromePath();
        BrowserType.LaunchOptions opts = new BrowserType.LaunchOptions().setHeadless(true);
        if (chromePath != null) opts.setExecutablePath(java.nio.file.Paths.get(chromePath));
        browser = playwright.chromium().launch(opts);
        System.out.println("[Setup] Browser launched.");
    }

    @AfterAll
    void afterAll() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    private BrowserContext newCtxWithRoutes() {
        BrowserContext ctx = browser.newContext();
        ctx.route(CLOCK_URL, r -> r.fulfill(
            new Route.FulfillOptions().setContentType("text/html").setBody(CLOCK_PAGE_HTML)));
        ctx.route(INACTIVITY_URL, r -> r.fulfill(
            new Route.FulfillOptions().setContentType("text/html").setBody(INACTIVITY_PAGE_HTML)));
        return ctx;
    }

    // ─────────────────────────────────────────────────────────────
    // 1. setFixedTime：固定 Date.now()，定时器仍运行
    // ─────────────────────────────────────────────────────────────

    /**
     * setFixedTime 让 Date.now() / new Date() 始终返回指定时间，
     * 但 setInterval / setTimeout 仍按真实节奏触发。
     *
     * 适合：只需要冻结"当前时间显示"，不需要控制定时器触发时机的场景。
     *
     * 重要：setFixedTime 必须在 navigate 之前调用！
     */
    @Test
    void setFixedTime() throws Exception {
        try (BrowserContext ctx = newCtxWithRoutes()) {
            Page page = ctx.newPage();

            // 在导航前设置固定时间
            page.clock().setFixedTime(FMT.parse("2024-02-02T10:00:00"));
            page.navigate(CLOCK_URL);

            // 页面初始化时 new Date() 返回 2024-02-02 10:00:00
            assertThat(page.getByTestId("current-time")).containsText("2/2/2024");
            assertThat(page.getByTestId("current-time")).containsText("10:00:00 AM");

            // 更新固定时间到 10:30（不需要等 30 分钟！）
            page.clock().setFixedTime(FMT.parse("2024-02-02T10:30:00"));

            // 断言显示更新为 10:30（等 setInterval 触发后更新）
            assertThat(page.getByTestId("current-time")).hasText("2/2/2024, 10:30:00 AM");

            System.out.println("[Test] setFixedTime passed.");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 2. install + pauseAt + fastForward：完整时间控制
    // ─────────────────────────────────────────────────────────────

    /**
     * install 初始化虚拟时钟（接管 Date + 所有定时器），
     * 之后可以用 pauseAt / fastForward / runFor / resume 精确控制时间流动。
     *
     * 典型场景：模拟"合上笔记本盖子，N 小时后再打开"
     *   - install(8:00)  → 页面从 8:00 开始正常运行
     *   - pauseAt(10:00) → 时间推进到 10:00 并暂停
     *   - fastForward("30:00") → 再快进 30 分钟到 10:30
     *
     * 注意：install 必须在 navigate 之前调用！
     */
    @Test
    void installPauseAtFastForward() throws Exception {
        try (BrowserContext ctx = newCtxWithRoutes()) {
            Page page = ctx.newPage();

            // install 在 navigate 之前调用
            page.clock().install(new Clock.InstallOptions()
                .setTime(FMT.parse("2024-02-02T08:00:00")));
            page.navigate(CLOCK_URL);

            // 快进到 10:00 并暂停
            page.clock().pauseAt(FMT.parse("2024-02-02T10:00:00"));
            assertThat(page.getByTestId("current-time")).hasText("2/2/2024, 10:00:00 AM");

            // 再快进 30 分钟（格式 "mm:ss" 或毫秒数）
            page.clock().fastForward("30:00");
            assertThat(page.getByTestId("current-time")).hasText("2/2/2024, 10:30:00 AM");

            System.out.println("[Test] installPauseAtFastForward passed.");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 3. 测试非活跃登出（超时场景）
    // ─────────────────────────────────────────────────────────────

    /**
     * 非活跃登出通常需要等 5~30 分钟，测试中绝对不能真实等待。
     * 用 fastForward 瞬间跳过等待时间，触发所有到期的 setTimeout。
     *
     * fastForward 语义：像"合上笔记本盖子再打开"——
     *   所有到期的定时器会立即一次性全部触发。
     */
    @Test
    void inactivityLogout() throws Exception {
        try (BrowserContext ctx = newCtxWithRoutes()) {
            Page page = ctx.newPage();

            // 安装虚拟时钟（使用当前时间作为起点）
            page.clock().install();
            page.navigate(INACTIVITY_URL);

            // 确认初始状态：用户在线
            assertThat(page.getByTestId("status")).containsText("logged out in");

            // 快进 5 分钟，触发所有到期的 setTimeout
            page.clock().fastForward("05:00");

            // 验证登出消息出现（无需真实等待 5 分钟）
            assertThat(page.getByTestId("status"))
                .hasText("Logged out due to inactivity.");

            System.out.println("[Test] inactivityLogout passed.");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 4. runFor：手动精确步进，逐毫秒触发定时器
    // ─────────────────────────────────────────────────────────────

    /**
     * runFor 与 fastForward 的区别：
     *   fastForward → 跳过时间，所有到期定时器立即一次性触发
     *   runFor      → 逐步推进，每个定时器按其原本触发顺序依次触发
     *                 适合需要验证"中间过程状态"的场景
     *
     * 本示例：暂停在 10:00:00，用 runFor(2000) 推进 2 秒，
     *         页面 setInterval(1000ms) 会触发 2 次，时间更新到 10:00:02。
     */
    @Test
    void runFor() throws Exception {
        try (BrowserContext ctx = newCtxWithRoutes()) {
            Page page = ctx.newPage();

            page.clock().install(new Clock.InstallOptions()
                .setTime(FMT.parse("2024-02-02T08:00:00")));
            page.navigate(CLOCK_URL);

            // 暂停在 10:00:00
            page.clock().pauseAt(FMT.parse("2024-02-02T10:00:00"));
            assertThat(page.getByTestId("current-time")).hasText("2/2/2024, 10:00:00 AM");

            // 精确步进 2 秒（触发 2 次 setInterval(1000ms)）
            page.clock().runFor(2000);
            assertThat(page.getByTestId("current-time")).hasText("2/2/2024, 10:00:02 AM");

            System.out.println("[Test] runFor passed.");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 工具方法：探测系统 Chrome 路径
    // ─────────────────────────────────────────────────────────────

    private static String resolveChromePath() {
        String[] candidates = {
            "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
            "/usr/bin/google-chrome",
            "/usr/bin/chromium-browser",
        };
        for (String p : candidates) {
            if (new java.io.File(p).exists()) return p;
        }
        return null;
    }
}
