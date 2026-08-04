package com.research.playwright;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import java.util.HashMap;
import java.util.Map;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B35 - Snapshot Testing (aria-snapshots) 章节测试
 *
 * ⚠️ 注意：matchesAriaSnapshot() API 在 Playwright Java 1.56.0 中不存在。
 * 此 API 在 Playwright Java 1.47.0+ (Node.js) 中可用，但 Java 版本尚未跟进。
 *
 * 本章内容（供未来版本参考）：
 *   通过 matchesAriaSnapshot() 将页面的无障碍树（accessibility tree）
 *   与预定义的 YAML 快照模板进行断言比对。
 *
 *   1. 基本快照匹配
 *   2. 部分匹配（省略属性或名称）
 *   3. 正则表达式匹配
 *   4. 生成快照（ariaSnapshot()）
 *   5. 属性与状态匹配
 *   6. Locator 级别快照
 *
 * 当 Playwright Java 升级到支持此 API 的版本时，取消 @Disabled 注解即可运行。
 *
 * 运行方式：
 *   mvn test -Dtest=B35_SnapshotTesting
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
@Disabled("matchesAriaSnapshot API not available in Playwright Java 1.56.0")
public class B35_SnapshotTesting {

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

    // ─────────────────────────────────────────────────────────────
    // 1. 基本快照匹配
    // ─────────────────────────────────────────────────────────────

    @Test @Order(1)
    void basicAriaSnapshot() {
        try (BrowserContext ctx = browser.newContext()) {
            ctx.route("**/*", route -> route.fulfill(new Route.FulfillOptions()
                    .setContentType("text/html; charset=utf-8")
                    .setBody("<html><body>" +
                            "<h1>Title</h1>" +
                            "<button>Submit</button>" +
                            "</body></html>")));

            Page page = ctx.newPage();
            page.navigate(BASE_URL + "/page");

            // 匹配基本快照
            // assertThat(page).matchesAriaSnapshot("""
            //     - heading "Title" [level=1]
            //     - button "Submit"
            //     """);

            System.out.println("[Test] basicAriaSnapshot: API not available in 1.56.0");
        }
    }

    // 其余测试省略，等待 Playwright Java 升级后启用

    // ─────────────────────────────────────────────────────────────
    // 工具方法
    // ─────────────────────────────────────────────────────────────

    private static final java.util.List<String> COMMON_CHROME_PATHS = java.util.List.of(
            "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
            "/Applications/Chromium.app/Contents/MacOS/Chromium",
            "/usr/bin/google-chrome",
            "/usr/bin/chromium-browser",
            "C:/Program Files/Google/Chrome/Application/chrome.exe",
            "C:/Program Files (x86)/Google/Chrome/Application/chrome.exe"
    );

    private static String resolveChromePath() {
        String envChrome = System.getenv("CHROME_EXECUTABLE_PATH");
        if (envChrome != null && !envChrome.isBlank() && java.nio.file.Files.exists(java.nio.file.Paths.get(envChrome))) {
            return java.nio.file.Paths.get(envChrome).toAbsolutePath().toString();
        }
        for (String candidate : COMMON_CHROME_PATHS) {
            if (java.nio.file.Files.exists(java.nio.file.Paths.get(candidate))) {
                return java.nio.file.Paths.get(candidate).toAbsolutePath().toString();
            }
        }
        return null;
    }
}
