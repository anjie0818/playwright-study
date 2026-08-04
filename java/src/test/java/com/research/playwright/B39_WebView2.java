package com.research.playwright;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B39 - WebView2 章节测试
 * 
 * 本章核心：
 *   WebView2 是 Windows 特有的控件，使用 Microsoft Edge 渲染 Web 内容。
 *   Playwright 通过 Chrome DevTools Protocol (CDP) 连接 WebView2 进行自动化。
 * 
 * ⚠️ 重要限制：
 *   - WebView2 仅适用于 Windows 10/11
 *   - 需要安装 Microsoft Edge WebView2 Runtime
 *   - 需要有使用 WebView2 控件的 WinForms 应用程序
 *   - 本测试在非 Windows 平台上标记为 @DisabledOnOs
 * 
 * 连接方式：
 *   1. 设置环境变量 WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS=--remote-debugging-port=9222
 *   2. 使用 playwright.chromium().connectOverCDP("http://localhost:9222") 连接
 * 
 * 运行方式：
 *   mvn test -Dtest=B39_WebView2  (仅在 Windows 上运行)
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisabledOnOs({OS.MAC, OS.LINUX})
public class B39_WebView2 {

    private Playwright playwright;
    private Browser browser;
    private WebView2Process webview2Process;

    @BeforeAll
    void beforeAll() throws IOException {
        playwright = Playwright.create();
        webview2Process = new WebView2Process();
        browser = playwright.chromium().connectOverCDP("http://127.0.0.1:" + webview2Process.cdpPort);
    }

    @AfterAll
    void afterAll() {
        if (browser != null) {
            browser.close();
        }
        if (webview2Process != null) {
            webview2Process.dispose();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 1. 通过 CDP 连接 WebView2
    // ─────────────────────────────────────────────────────────────

    @Test @Order(1)
    void connectOverCDP() {
        assertNotNull(browser, "Browser should be connected via CDP");
        
        BrowserContext context = browser.contexts().get(0);
        assertNotNull(context, "Should have browser context");
        
        Page page = context.pages().get(0);
        assertNotNull(page, "Should have page");
        
        System.out.println("[Test] connectOverCDP: connected on port " + webview2Process.cdpPort);
    }

    // ─────────────────────────────────────────────────────────────
    // 2. 导航到网页
    // ─────────────────────────────────────────────────────────────

    @Test @Order(2)
    void navigateInWebView2() {
        BrowserContext context = browser.contexts().get(0);
        Page page = context.pages().get(0);
        
        page.navigate("https://playwright.dev");
        
        // 验证页面加载
        assertTrue(page.url().contains("playwright.dev"), "Should navigate to playwright.dev");
        
        System.out.println("[Test] navigateInWebView2: " + page.url());
    }

    // ─────────────────────────────────────────────────────────────
    // 3. 定位和交互
    // ─────────────────────────────────────────────────────────────

    @Test @Order(3)
    void interactWithElements() {
        BrowserContext context = browser.contexts().get(0);
        Page page = context.pages().get(0);
        
        page.navigate("https://playwright.dev");
        
        // 定位元素
        Locator getStarted = page.getByText("Get started");
        assertTrue(getStarted.isVisible(), "Get started link should be visible");
        
        // 点击导航
        getStarted.click();
        
        // 验证导航成功
        assertTrue(page.url().contains("intro"), "Should navigate to intro page");
        
        System.out.println("[Test] interactWithElements: navigated to " + page.url());
    }

    // ─────────────────────────────────────────────────────────────
    // 4. 表单填写
    // ─────────────────────────────────────────────────────────────

    @Test @Order(4)
    void fillForm() {
        BrowserContext context = browser.contexts().get(0);
        Page page = context.newPage();
        
        page.navigate("https://example.com");
        
        // 填写表单（假设有表单）
        Locator heading = page.locator("h1");
        assertTrue(heading.isVisible(), "Page should have heading");
        
        String title = page.title();
        assertNotNull(title, "Page should have title");
        
        System.out.println("[Test] fillForm: page title = " + title);
        
        page.close();
    }

    // ─────────────────────────────────────────────────────────────
    // 5. 多页面操作
    // ─────────────────────────────────────────────────────────────

    @Test @Order(5)
    void multiplePages() {
        BrowserContext context = browser.contexts().get(0);
        
        Page page1 = context.newPage();
        page1.navigate("https://example.com");
        
        Page page2 = context.newPage();
        page2.navigate("https://playwright.dev");
        
        // 验证两个页面都存在
        assertTrue(page1.url().contains("example.com"), "Page 1 should be on example.com");
        assertTrue(page2.url().contains("playwright.dev"), "Page 2 should be on playwright.dev");
        
        System.out.println("[Test] multiplePages: page1=" + page1.url() + ", page2=" + page2.url());
        
        page1.close();
        page2.close();
    }

    // ─────────────────────────────────────────────────────────────
    // WebView2 进程管理类
    // ─────────────────────────────────────────────────────────────

    /**
     * 管理 WebView2 应用程序进程
     * 
     * 注意：需要预先编译的 WebView2 WinForms 应用程序
     * 默认路径：../webview2-app/bin/Debug/net8.0-windows/webview2.exe
     */
    static class WebView2Process {
        int cdpPort;
        private Path dataDir;
        private Process process;
        private Path executablePath = Path.of("../webview2-app/bin/Debug/net8.0-windows/webview2.exe");

        WebView2Process() throws IOException {
            cdpPort = nextFreePort();
            dataDir = Files.createTempDirectory("pw-java-webview2-tests-");

            if (!Files.exists(executablePath)) {
                throw new RuntimeException("WebView2 executable not found: " + executablePath);
            }

            ProcessBuilder pb = new ProcessBuilder().command(executablePath.toAbsolutePath().toString());
            java.util.Map<String, String> envMap = pb.environment();
            envMap.put("WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS", "--remote-debugging-port=" + cdpPort);
            envMap.put("WEBVIEW2_USER_DATA_FOLDER", dataDir.toString());
            process = pb.start();

            // 等待 WebView2 初始化
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()));
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    throw new RuntimeException("WebView2 process exited");
                }
                if (line.contains("WebView2 initialized")) {
                    break;
                }
            }
        }

        private static final AtomicInteger nextUnusedPort = new AtomicInteger(9000);

        private static boolean available(int port) {
            try (java.net.ServerSocket ignored = new java.net.ServerSocket(port)) {
                return true;
            } catch (IOException ignored) {
                return false;
            }
        }

        static int nextFreePort() {
            for (int i = 0; i < 100; i++) {
                int port = nextUnusedPort.getAndIncrement();
                if (available(port)) {
                    return port;
                }
            }
            throw new RuntimeException("Cannot find free port: " + nextUnusedPort.get());
        }

        void dispose() {
            process.destroy();
            try {
                process.waitFor();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
