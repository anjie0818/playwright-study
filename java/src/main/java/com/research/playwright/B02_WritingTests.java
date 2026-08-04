package com.research.playwright;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * B02 - 官方文档 Writing tests 章节实践
 *
 * 本章核心：
 *   1. Assertions 自动重试断言
 *   2. Locators 定位器（优先 getByRole）
 *   3. Test Isolation 用 BrowserContext 隔离每个测试
 *
 * 练习目标：
 *   访问 playwright.dev，点击 "Get started"，
 *   验证页面跳转到 Installation 页面。
 *
 * 浏览器启动策略（与框架 BrowserManager 一致）：
 *   - 探测系统已安装的 Chrome
 *   - 找到则设置 PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 跳过下载
 *   - 使用 setExecutablePath 指向本地 Chrome
 *
 * 运行方式：
 *   mvn compile exec:java -Dexec.mainClass="com.research.playwright.B02_WritingTests"
 */
public class B02_WritingTests {

    // macOS / Linux / Windows 常见 Chrome 路径
    private static final List<String> COMMON_CHROME_PATHS = List.of(
            "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
            "/Applications/Chromium.app/Contents/MacOS/Chromium",
            "/usr/bin/google-chrome",
            "/usr/bin/chromium-browser",
            "C:/Program Files/Google/Chrome/Application/chrome.exe",
            "C:/Program Files (x86)/Google/Chrome/Application/chrome.exe"
    );

    public static void main(String[] args) {
        // 探测本地 Chrome；若找到则跳过 Playwright 内置浏览器下载
        Map<String, String> env = new HashMap<>(System.getenv());
        String chromePath = resolveChromePath();
        if (chromePath != null) {
            env.put("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1");
            System.out.println("[Env] Found local Chrome: " + chromePath + ", skip download");
        } else {
            System.out.println("[Env] No local Chrome, will download Playwright built-in Chromium");
        }

        try (Playwright playwright = Playwright.create(new Playwright.CreateOptions().setEnv(env))) {
            BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                    .setHeadless(false);
            if (chromePath != null) {
                launchOptions.setExecutablePath(Paths.get(chromePath));
            }
            Browser browser = playwright.chromium().launch(launchOptions);

            // ═══════════════════════════════════════════════════
            // 测试 1：验证首页标题 + 点击 Get started
            // ═══════════════════════════════════════════════════
            // 每个测试创建独立的 BrowserContext（测试隔离）
            try (BrowserContext context = browser.newContext()) {
                Page page = context.newPage();
                page.navigate("https://playwright.dev/");

                // Assertions：标题包含 "Playwright"
                assertThat(page).hasTitle(Pattern.compile("Playwright"));
                System.out.println("[Test1] Page title matches 'Playwright'");

                // Locators：用 getByRole 定位 "Get started" 链接
                Locator getStarted = page.getByRole(AriaRole.LINK,
                        new Page.GetByRoleOptions().setName("Get started"));

                // Assertions：验证链接 href 属性
                assertThat(getStarted).hasAttribute("href", "/docs/intro");
                System.out.println("[Test1] Get started link href is /docs/intro");

                // Action：点击链接
                getStarted.click();

                // Assertions：页面出现 "Installation" 标题
                assertThat(page.getByRole(AriaRole.HEADING,
                        new Page.GetByRoleOptions().setName("Installation"))).isVisible();
                System.out.println("[Test1] Installation heading is visible, URL=" + page.url());
            }

            // ═══════════════════════════════════════════════════
            // 测试 2：另一个独立 Context，验证测试隔离
            // ═══════════════════════════════════════════════════
            try (BrowserContext context = browser.newContext()) {
                Page page = context.newPage();
                page.navigate("https://playwright.dev/");

                Locator getStarted = page.getByRole(AriaRole.LINK,
                        new Page.GetByRoleOptions().setName("Get started"));
                assertThat(getStarted).isVisible();
                System.out.println("[Test2] In isolated context, Get started link is also visible");
            }

            System.out.println("[Done] Writing tests chapter practiced");
        }
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
