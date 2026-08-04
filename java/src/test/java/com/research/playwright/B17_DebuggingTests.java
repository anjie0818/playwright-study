package com.research.playwright;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B17 - 官方文档 Debugging Tests 章节实践
 *
 * 本章核心（5 种调试手段）：
 *   1. Playwright Inspector — PWDEBUG=1 打开 GUI 调试器，逐步执行、编辑定位器
 *   2. Browser DevTools     — PWDEBUG=console 在浏览器控制台暴露 playwright 对象
 *   3. Verbose API Logs     — DEBUG=pw:api 输出 Playwright 内部 API 调用日志
 *   4. Headed + SlowMo      — 有头模式 + 每个操作减慢 N 毫秒，肉眼跟随执行
 *   5. page.pause()         — 代码中设置断点，运行到此处暂停
 *
 * Trace Viewer 已在 B05 中覆盖，本章不重复。
 *
 * 环境变量驱动的调试模式（Inspector / DevTools）无法在 JUnit 自动化测试中演示，
 * 本章侧重代码可控的部分：SlowMo、page.pause() 用法、Verbose Logs。
 *
 * 运行方式：
 *   mvn test -Dtest=B17_DebuggingTests
 *
 * 手动体验 Inspector：
 *   PWDEBUG=1 PLAYWRIGHT_JAVA_SRC=src/test/java mvn test -Dtest=B17_DebuggingTests#slowMoDemo
 *
 * 手动体验 Verbose Logs：
 *   DEBUG=pw:api mvn test -Dtest=B17_DebuggingTests#verboseApiLogsDemo
 */
public class B17_DebuggingTests {

    // ─────────────────────────────────────────────────────────────
    // 1. SlowMo 模式：每个操作减慢 100ms，方便肉眼跟随（headless 下也有效）
    // ─────────────────────────────────────────────────────────────

    /**
     * SlowMo 演示：用 setSlowMo(100) 让每个 Playwright 操作之间间隔 100ms。
     *
     * headless 模式下 SlowMo 仍然生效（操作间隔变长），
     * 但肉眼看不到浏览器窗口。要真正"看到"执行过程，需配合 setHeadless(false)。
     *
     * 本测试在 headless + slowMo 下运行，验证操作最终结果正确，
     * 并通过时间差感知 slowMo 的效果。
     */
    @Test
    void slowMoDemo() {
        Map<String, String> env = new HashMap<>(System.getenv());
        String chromePath = resolveChromePath();
        if (chromePath != null) {
            env.put("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1");
        }

        try (Playwright pw = Playwright.create(new Playwright.CreateOptions().setEnv(env))) {
            BrowserType.LaunchOptions opts = new BrowserType.LaunchOptions()
                    .setHeadless(true)   // CI 友好；手动调试时改 false
                    .setSlowMo(100);     // 每个操作减慢 100ms
            if (chromePath != null) {
                opts.setExecutablePath(Paths.get(chromePath));
            }

            long start = System.currentTimeMillis();
            try (Browser browser = pw.chromium().launch(opts);
                 BrowserContext ctx = browser.newContext();
                 Page page = ctx.newPage()) {

                page.setContent(
                    "<form onsubmit='event.preventDefault(); window.result=name.value'>" +
                    "<input id='name' name='name' type='text' value='Playwright'/>" +
                    "<button type='submit'>Submit</button></form>");

                page.locator("button").click();
                assertEquals("Playwright", page.evaluate("result"));
            }

            long elapsed = System.currentTimeMillis() - start;
            // slowMo=100 下，setContent + click 至少各加 100ms，总时间应明显大于无 slowMo
            System.out.println("[Test] slowMoDemo passed. elapsed=" + elapsed + "ms (slowMo=100ms/op)");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 2. Headed 模式：有头浏览器，肉眼调试（默认 Disabled，需手动启用）
    // ─────────────────────────────────────────────────────────────

    /**
     * Headed 模式演示：打开可见浏览器窗口 + SlowMo 减速。
     *
     * 适合本地调试时观察执行过程。CI 环境无显示器，默认禁用。
     * 手动运行：移除 @Disabled 后执行
     *   mvn test -Dtest=B17_DebuggingTests#headedDemo
     */
    @Test
    @Disabled("需要显示器环境，CI 下跳过。手动调试时移除 @Disabled")
    void headedDemo() {
        Map<String, String> env = new HashMap<>(System.getenv());
        String chromePath = resolveChromePath();
        if (chromePath != null) {
            env.put("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1");
        }

        try (Playwright pw = Playwright.create(new Playwright.CreateOptions().setEnv(env))) {
            BrowserType.LaunchOptions opts = new BrowserType.LaunchOptions()
                    .setHeadless(false)  // 有头模式
                    .setSlowMo(500);     // 500ms/op，肉眼可跟随
            if (chromePath != null) {
                opts.setExecutablePath(Paths.get(chromePath));
            }

            try (Browser browser = pw.chromium().launch(opts);
                 BrowserContext ctx = browser.newContext();
                 Page page = ctx.newPage()) {

                page.navigate("https://playwright.dev/");
                page.getByRole(AriaRole.LINK,
                        new Page.GetByRoleOptions().setName("Get started")).click();

                // 简单断言，主要目的是让浏览器窗口停留一会儿方便观察
                assertTrue(page.url().contains("/docs/intro"));
                System.out.println("[Test] headedDemo passed. 观察浏览器窗口的执行过程。");
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 3. page.pause() 断点：代码中设置暂停点（默认 Disabled）
    // ─────────────────────────────────────────────────────────────

    /**
     * page.pause() 演示：在指定位置暂停执行，配合 Inspector 逐步调试。
     *
     * 运行前需设置环境变量：
     *   PWDEBUG=1 PLAYWRIGHT_JAVA_SRC=src/test/java mvn test -Dtest=B17_DebuggingTests#pagePauseDemo
     *
     * Inspector 打开后：
     *   - 顶部工具栏：▶ Resume / ⏸ Pause / ⏭ Step over
     *   - Pick Locator：实时编辑定位器，浏览器中高亮匹配元素
     *   - Actionability log：查看元素是否 visible/stable/enabled
     */
    @Test
    @Disabled("page.pause() 会阻塞执行等待人工操作，自动化 CI 下跳过")
    void pagePauseDemo() {
        Map<String, String> env = new HashMap<>(System.getenv());
        String chromePath = resolveChromePath();
        if (chromePath != null) {
            env.put("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1");
        }

        try (Playwright pw = Playwright.create(new Playwright.CreateOptions().setEnv(env))) {
            BrowserType.LaunchOptions opts = new BrowserType.LaunchOptions().setHeadless(false);
            if (chromePath != null) {
                opts.setExecutablePath(Paths.get(chromePath));
            }

            try (Browser browser = pw.chromium().launch(opts);
                 BrowserContext ctx = browser.newContext();
                 Page page = ctx.newPage()) {

                page.setContent(
                    "<form onsubmit='event.preventDefault(); window.result=email.value'>" +
                    "<input id='email' name='email' type='email' value='test@example.com'/>" +
                    "<button type='submit'>Subscribe</button></form>");

                // ← 断点：运行到这里会暂停，Inspector 弹出
                // 在 Inspector 中可以：
                //   1. 编辑 Pick Locator 字段试试 "input#email" 或 "text=Subscribe"
                //   2. 查看 Actionability log
                //   3. 点击 Resume 继续执行
                page.pause();

                page.locator("button").click();
                assertEquals("test@example.com", page.evaluate("result"));
                System.out.println("[Test] pagePauseDemo passed.");
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 4. Verbose API Logs：DEBUG=pw:api 输出内部 API 调用日志
    // ─────────────────────────────────────────────────────────────

    /**
     * Verbose API Logs 演示。
     *
     * 通过代码设置 DEBUG=pw:api，Playwright 会在 stderr 输出每个内部 API 调用，
     * 包括：等待元素、点击坐标、导航状态等。
     *
     * 运行方式：
     *   mvn test -Dtest=B17_DebuggingTests#verboseApiLogsDemo
     *
     * verbose 日志输出在 stderr，Maven 默认会显示。
     * 如需过滤查看：mvn test -Dtest=B17_DebuggingTests#verboseApiLogsDemo 2>&1 | grep pw:api
     */
    @Test
    void verboseApiLogsDemo() {
        Map<String, String> env = new HashMap<>(System.getenv());
        String chromePath = resolveChromePath();
        if (chromePath != null) {
            env.put("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1");
        }

        // 通过代码设置 DEBUG=pw:api，等价于命令行 DEBUG=pw:api mvn test
        env.put("DEBUG", "pw:api");
        System.out.println("[Test] verboseApiLogsDemo: DEBUG=pw:api (set via code)");

        try (Playwright pw = Playwright.create(new Playwright.CreateOptions().setEnv(env))) {
            BrowserType.LaunchOptions opts = new BrowserType.LaunchOptions().setHeadless(true);
            if (chromePath != null) {
                opts.setExecutablePath(Paths.get(chromePath));
            }

            try (Browser browser = pw.chromium().launch(opts);
                 BrowserContext ctx = browser.newContext();
                 Page page = ctx.newPage()) {

                page.setContent(
                    "<button onclick='this.textContent=\"Done\"'>Click me</button>");
                page.locator("button").click();
                assertEquals("Done", page.locator("button").textContent());
            }
        }
        System.out.println("[Test] verboseApiLogsDemo passed. 检查 stderr 中的 [pw:api] 日志。");
    }

    // ─────────────────────────────────────────────────────────────
    // 5. PWDEBUG=console：浏览器 DevTools 中的 playwright 对象（默认 Disabled）
    // ─────────────────────────────────────────────────────────────

    /**
     * Browser DevTools 调试演示。
     *
     * 运行方式：
     *   PWDEBUG=console PLAYWRIGHT_JAVA_SRC=src/test/java \
     *     mvn test -Dtest=B17_DebuggingTests#devToolsDemo
     *
     * 浏览器打开后，在 DevTools Console 中可用：
     *   playwright.$('button')           — 查询单个元素
     *   playwright.$$('li')              — 查询所有匹配元素
     *   playwright.inspect('text=Done')  — 在 Elements 面板定位元素
     *   playwright.locator('button')     — 创建 Locator
     *   playwright.selector($0)          — 为选中的元素生成 selector
     */
    @Test
    @Disabled("PWDEBUG=console 需要人工在 DevTools 中操作，CI 下跳过")
    void devToolsDemo() {
        Map<String, String> env = new HashMap<>(System.getenv());
        String chromePath = resolveChromePath();
        if (chromePath != null) {
            env.put("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1");
        }

        try (Playwright pw = Playwright.create(new Playwright.CreateOptions().setEnv(env))) {
            BrowserType.LaunchOptions opts = new BrowserType.LaunchOptions().setHeadless(false);
            if (chromePath != null) {
                opts.setExecutablePath(Paths.get(chromePath));
            }

            try (Browser browser = pw.chromium().launch(opts);
                 BrowserContext ctx = browser.newContext();
                 Page page = ctx.newPage()) {

                page.setContent(
                    "<ul><li>Apple</li><li>Banana</li><li>Cherry</li></ul>" +
                    "<button onclick='this.textContent=\"Done\"'>Click me</button>");

                // 暂停后打开 DevTools (F12)，在 Console 中试试：
                //   playwright.$('button')
                //   playwright.$$('li')
                //   playwright.inspect('text=Apple')
                //   playwright.selector($0)  // 先在 Elements 面板选中一个元素
                page.pause();
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
