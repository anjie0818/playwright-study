package com.research.playwright;

import com.microsoft.playwright.*;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * B03 - 官方文档 Generating tests 章节实践
 *
 * 本章核心：Codegen 录制生成测试代码
 *
 * 官方用法：
 *   mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="codegen demo.playwright.dev/todomvc"
 *   这会打开浏览器 + Inspector，你在浏览器操作，Inspector 生成代码。
 *
 * 本示例用法（代码中打开 Inspector）：
 *   运行程序 → 自动打开浏览器和 Playwright Inspector → 你手动操作 →
 *   在 Inspector 里点击 Copy 复制生成的代码 → 关闭 Inspector → 程序结束。
 *
 * 运行方式：
 *   mvn compile exec:java -Dexec.mainClass="com.research.playwright.B03_GeneratingTests"
 */
public class B03_GeneratingTests {

    private static final List<String> COMMON_CHROME_PATHS = List.of(
            "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
            "/Applications/Chromium.app/Contents/MacOS/Chromium",
            "/usr/bin/google-chrome",
            "/usr/bin/chromium-browser",
            "C:/Program Files/Google/Chrome/Application/chrome.exe",
            "C:/Program Files (x86)/Google/Chrome/Application/chrome.exe"
    );

    public static void main(String[] args) {
        Map<String, String> env = new HashMap<>(System.getenv());
        String chromePath = resolveChromePath();
        if (chromePath != null) {
            env.put("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1");
        }

        try (Playwright playwright = Playwright.create(new Playwright.CreateOptions().setEnv(env))) {
            BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                    .setHeadless(false); // 必须 headed 才能看到 Inspector
            if (chromePath != null) {
                launchOptions.setExecutablePath(Paths.get(chromePath));
            }
            Browser browser = playwright.chromium().launch(launchOptions);

            BrowserContext context = browser.newContext();
            Page page = context.newPage();

            // 导航到 todomvc 示例页面
            page.navigate("https://demo.playwright.dev/todomvc");

            System.out.println("==========================================");
            System.out.println("Playwright Inspector 已打开。");
            System.out.println("请在浏览器中操作，Inspector 会生成对应代码。");
            System.out.println("操作完成后，关闭 Inspector 窗口结束程序。");
            System.out.println("==========================================");

            // 暂停页面，打开 Playwright Inspector 录制界面
            page.pause();

            System.out.println("[Done] Inspector 已关闭，codegen 演示结束");
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
