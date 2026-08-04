package com.research.playwright;

import com.microsoft.playwright.*;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * B02b - 补充：直观演示 BrowserContext 隔离
 *
 * 实验：
 *   Context A：访问页面，写入 localStorage.username = "Alice"
 *   Context B：访问同一页面，读取 localStorage.username
 *
 * 预期结果：
 *   Context B 读不到 "Alice"，因为两个 Context 存储完全隔离。
 */
public class B02b_ContextIsolationDemo {

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
            BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions().setHeadless(false);
            if (chromePath != null) {
                launchOptions.setExecutablePath(Paths.get(chromePath));
            }
            Browser browser = playwright.chromium().launch(launchOptions);

            // ═══════════════════════════════════════════════════
            // Context A：写入 localStorage
            // ═══════════════════════════════════════════════════
            try (BrowserContext contextA = browser.newContext()) {
                Page pageA = contextA.newPage();
                pageA.navigate("https://playwright.dev/");
                pageA.evaluate("localStorage.setItem('username', 'Alice')");

                Object valueA = pageA.evaluate("localStorage.getItem('username')");
                System.out.println("[Context A] localStorage.username = " + valueA);
            }

            // ═══════════════════════════════════════════════════
            // Context B：读取 localStorage
            // ═══════════════════════════════════════════════════
            try (BrowserContext contextB = browser.newContext()) {
                Page pageB = contextB.newPage();
                pageB.navigate("https://playwright.dev/");

                Object valueB = pageB.evaluate("localStorage.getItem('username')");
                System.out.println("[Context B] localStorage.username = " + valueB);
            }

            // ═══════════════════════════════════════════════════
            // Context C：用 storageState 复用 Context A 的状态
            // ═══════════════════════════════════════════════════
            // 先拿到 Context A 的 storageState，再创建 Context C 时注入
            // 这样 Context C 就能读到 "Alice" 了
            String storageState;
            try (BrowserContext contextA = browser.newContext()) {
                Page pageA = contextA.newPage();
                pageA.navigate("https://playwright.dev/");
                pageA.evaluate("localStorage.setItem('username', 'Alice')");
                storageState = contextA.storageState();
            }

            try (BrowserContext contextC = browser.newContext(
                    new Browser.NewContextOptions().setStorageState(storageState))) {
                Page pageC = contextC.newPage();
                pageC.navigate("https://playwright.dev/");

                Object valueC = pageC.evaluate("localStorage.getItem('username')");
                System.out.println("[Context C] 注入 storageState 后，localStorage.username = " + valueC);
            }

            System.out.println("[Done] Context isolation demonstrated");
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
