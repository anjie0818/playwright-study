package com.research.playwright;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * B05 - 官方文档 Trace viewer 章节实践
 *
 * 本章核心：
 *   使用 BrowserContext.tracing() 录制测试执行轨迹，生成 trace.zip。
 *   trace 里包含：
 *     - screenshots: 屏幕截图/录屏胶片
 *     - snapshots: 每个 action 前后的 DOM 快照
 *     - sources: 对应的 Java 源代码位置
 *
 * 查看 trace：
 *   mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="show-trace target/trace/trace.zip"
 *
 * 运行测试：
 *   mvn test -Dtest=B05_TraceViewer
 */
public class B05_TraceViewer {

    static Playwright playwright;
    static Browser browser;

    BrowserContext context;
    Page page;

    // trace 文件保存路径
    static final Path TRACE_DIR = Paths.get("target", "trace");
    static final Path TRACE_FILE = TRACE_DIR.resolve("trace.zip");

    private static final List<String> COMMON_CHROME_PATHS = List.of(
            "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
            "/Applications/Chromium.app/Contents/MacOS/Chromium",
            "/usr/bin/google-chrome",
            "/usr/bin/chromium-browser",
            "C:/Program Files/Google/Chrome/Application/chrome.exe",
            "C:/Program Files (x86)/Google/Chrome/Application/chrome.exe"
    );

    @BeforeAll
    static void launchBrowser() throws Exception {
        Files.createDirectories(TRACE_DIR);

        Map<String, String> env = new HashMap<>(System.getenv());
        String chromePath = resolveChromePath();
        if (chromePath != null) {
            env.put("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1");
            System.out.println("[@BeforeAll] Use local Chrome: " + chromePath);
        }

        playwright = Playwright.create(new Playwright.CreateOptions().setEnv(env));

        BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions().setHeadless(true);
        if (chromePath != null) {
            launchOptions.setExecutablePath(Paths.get(chromePath));
        }
        browser = playwright.chromium().launch(launchOptions);
    }

    @AfterAll
    static void closeBrowser() {
        playwright.close();
        System.out.println("[@AfterAll] Browser and Playwright closed");
    }

    @BeforeEach
    void createContextAndPage() {
        context = browser.newContext();

        // 关键：在创建 Page 之前开启 tracing
        context.tracing().start(new Tracing.StartOptions()
                .setScreenshots(true)
                .setSnapshots(true)
                .setSources(true));

        page = context.newPage();
    }

    @AfterEach
    void closeContextAndStopTracing() {
        // 关键：在关闭 context 之前停止 tracing，并输出到文件
        context.tracing().stop(new Tracing.StopOptions().setPath(TRACE_FILE));
        System.out.println("[@AfterEach] Trace saved to: " + TRACE_FILE.toAbsolutePath());
        context.close();
    }

    @Test
    void shouldNavigateAndRecordTrace() {
        page.navigate("https://playwright.dev/");

        Locator getStarted = page.getByRole(AriaRole.LINK,
                new Page.GetByRoleOptions().setName("Get started"));
        assertThat(getStarted).isVisible();

        getStarted.click();

        assertThat(page.getByRole(AriaRole.HEADING,
                new Page.GetByRoleOptions().setName("Installation"))).isVisible();

        System.out.println("[Test] shouldNavigateAndRecordTrace passed, trace recorded");
    }

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
