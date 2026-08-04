package com.research.playwright;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B04 - 官方文档 Running and debugging tests 章节实践
 *
 * 本章核心：
 *   用 JUnit 5 管理 Playwright 测试生命周期：
 *   - @BeforeAll: 启动 Playwright + Browser（整个类只执行一次）
 *   - @BeforeEach: 每个测试方法前新建 Context + Page（隔离）
 *   - @AfterEach: 每个测试方法后关闭 Context
 *   - @AfterAll: 关闭 Browser + Playwright
 *
 * 运行方式：
 *   mvn test -Dtest=B04_RunningAndDebuggingTests
 */
public class B04_RunningAndDebuggingTests {

    // 整个测试类共享
    static Playwright playwright;
    static Browser browser;

    // 每个测试方法独立
    BrowserContext context;
    Page page;

    private static final List<String> COMMON_CHROME_PATHS = List.of(
            "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
            "/Applications/Chromium.app/Contents/MacOS/Chromium",
            "/usr/bin/google-chrome",
            "/usr/bin/chromium-browser",
            "C:/Program Files/Google/Chrome/Application/chrome.exe",
            "C:/Program Files (x86)/Google/Chrome/Application/chrome.exe"
    );

    @BeforeAll
    static void launchBrowser() {
        Map<String, String> env = new HashMap<>(System.getenv());
        String chromePath = resolveChromePath();
        if (chromePath != null) {
            env.put("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1");
            System.out.println("[@BeforeAll] Use local Chrome: " + chromePath);
        }

        playwright = Playwright.create(new Playwright.CreateOptions().setEnv(env));

        BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                .setHeadless(true); // 默认 headless，CI 友好
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
        page = context.newPage();
    }

    @AfterEach
    void closeContext() {
        context.close();
    }

    @Test
    void shouldClickButton() {
        page.navigate("data:text/html,<script>var result;</script><button onclick='result=\"Clicked\"'>Go</button>");
        page.locator("button").click();
        assertEquals("Clicked", page.evaluate("result"));
        System.out.println("[Test] shouldClickButton passed");
    }

    @Test
    void shouldCheckTheBox() {
        page.setContent("<input id='checkbox' type='checkbox'></input>");
        page.locator("input").check();
        assertTrue((Boolean) page.evaluate("() => window['checkbox'].checked"));
        System.out.println("[Test] shouldCheckTheBox passed");
    }

    @Test
    void shouldSearchAndSubmit() {
        // 用 data URL 构造一个本地表单，避免外部网站不稳定
        page.navigate("data:text/html,<form onsubmit='event.preventDefault(); window.result=search.value;'>" +
                "<input id='search' name='search' type='text'/>" +
                "<button type='submit'>Search</button></form>");
        page.locator("input[name=\"search\"]").fill("playwright");
        page.locator("button").click();
        assertEquals("playwright", page.evaluate("result"));
        System.out.println("[Test] shouldSearchAndSubmit passed");
    }

    @Test
    void shouldNavigateToPlaywrightDocs() {
        page.navigate("https://playwright.dev/");

        Locator getStarted = page.getByRole(AriaRole.LINK,
                new Page.GetByRoleOptions().setName("Get started"));
        assertThat(getStarted).isVisible();

        getStarted.click();
        assertThat(page).hasURL("https://playwright.dev/docs/intro");
        System.out.println("[Test] shouldNavigateToPlaywrightDocs passed");
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
