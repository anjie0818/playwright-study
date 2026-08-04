package com.research.playwright;

import com.microsoft.playwright.*;
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
 * B18 - 官方文档 Downloads 章节实践
 *
 * 本章核心：
 *   1. page.waitForDownload(() -> { 触发下载的操作 }) — 等待下载开始并获取 Download 对象
 *   2. download.saveAs(path) — 将下载文件保存到指定路径
 *   3. download.suggestedFilename() — 获取浏览器建议的文件名
 *   4. download.path() — 获取临时下载路径（Context 关闭后会被删除）
 *   5. page.onDownload(handler) — 事件式处理，适合不知道何时触发下载的场景
 *   6. BrowserType.launchOptions().setDownloadsPath() — 指定下载目录
 *
 *   关键注意：
 *     - 下载文件存在临时目录，BrowserContext 关闭后自动删除
 *     - saveAs 必须在 Context 关闭前调用（否则临时文件已被清理）
 *
 * 运行方式：
 *   mvn test -Dtest=B18_Downloads
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class B18_Downloads {

    private static final String PAGE_URL = "http://test.local/downloads";

    private Playwright playwright;
    private Browser browser;

    @BeforeAll
    void beforeAll() {
        Map<String, String> env = new HashMap<>(System.getenv());
        String chromePath = resolveChromePath();
        if (chromePath != null) {
            env.put("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1");
        }

        // 指定下载目录（可选，不设则用系统临时目录）
        Path downloadDir = Paths.get("target", "downloads");
        try {
            Files.createDirectories(downloadDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        playwright = Playwright.create(new Playwright.CreateOptions().setEnv(env));

        BrowserType.LaunchOptions opts = new BrowserType.LaunchOptions()
                .setHeadless(true)
                .setDownloadsPath(downloadDir);
        if (chromePath != null) {
            opts.setExecutablePath(Paths.get(chromePath));
        }
        browser = playwright.chromium().launch(opts);
        System.out.println("[Setup] Downloads path: " + downloadDir.toAbsolutePath());
    }

    @AfterAll
    void afterAll() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    /**
     * 用 route 拦截请求，返回带下载链接的 HTML 页面。
     * 点击链接时，route 返回 Content-Disposition: attachment 触发浏览器下载。
     */
    private void setupRoutes(BrowserContext ctx) {
        // 页面 HTML：三个下载链接
        String html = """
            <html><body>
            <h1>Download Test</h1>
            <a id="dl-txt" href="/file?name=hello.txt">Download TXT</a><br>
            <a id="dl-csv" href="/file?name=data.csv">Download CSV</a><br>
            <button id="dl-btn" onclick="window.location='/file?name=report.txt'">Download via Button</button>
            </body></html>
            """;

        ctx.route(PAGE_URL, r -> r.fulfill(
                new Route.FulfillOptions().setContentType("text/html").setBody(html)));

        // 下载文件路由：根据 name 参数返回不同内容，设置 attachment 头触发下载
        ctx.route("**/file?name=*", r -> {
            String fileParam = r.request().url().split("name=")[1];
            String content = switch (fileParam) {
                case "hello.txt" -> "Hello, Playwright!";
                case "data.csv" -> "name,age\\nAlice,30\\nBob,25";
                case "report.txt" -> "Q3 Report\\n==========\\nRevenue: $1M";
                default -> "unknown file";
            };
            r.fulfill(new Route.FulfillOptions()
                    .setStatus(200)
                    .setContentType("text/plain")
                    .setHeaders(Map.of(
                            "Content-Disposition", "attachment; filename=\"" + fileParam + "\""))
                    .setBody(content));
        });
    }

    // ─────────────────────────────────────────────────────────────
    // 1. 基本下载：waitForDownload + saveAs
    // ─────────────────────────────────────────────────────────────

    @Test
    void basicDownload() throws IOException {
        try (BrowserContext ctx = browser.newContext()) {
            setupRoutes(ctx);
            Page page = ctx.newPage();
            page.navigate(PAGE_URL);

            // waitForDownload：lambda 内执行触发下载的操作
            Download download = page.waitForDownload(() -> {
                page.locator("#dl-txt").click();
            });

            // suggestedFilename 来自 Content-Disposition 头
            assertEquals("hello.txt", download.suggestedFilename());

            // 保存到指定路径
            Path savePath = Paths.get("target", "downloads", download.suggestedFilename());
            download.saveAs(savePath);

            assertTrue(Files.exists(savePath));
            assertEquals("Hello, Playwright!", Files.readString(savePath));

            System.out.println("[Test] basicDownload passed. saved to: " + savePath);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 2. 不同文件类型下载
    // ─────────────────────────────────────────────────────────────

    @Test
    void downloadCsvFile() throws IOException {
        try (BrowserContext ctx = browser.newContext()) {
            setupRoutes(ctx);
            Page page = ctx.newPage();
            page.navigate(PAGE_URL);

            Download download = page.waitForDownload(() -> {
                page.locator("#dl-csv").click();
            });

            assertEquals("data.csv", download.suggestedFilename());

            Path savePath = Paths.get("target", "downloads", "my-data.csv");
            download.saveAs(savePath);

            String content = Files.readString(savePath);
            assertTrue(content.contains("Alice,30"));

            System.out.println("[Test] downloadCsvFile passed. content=\n" + content);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 3. 通过按钮触发下载
    // ─────────────────────────────────────────────────────────────

    @Test
    void downloadViaButton() throws IOException {
        try (BrowserContext ctx = browser.newContext()) {
            setupRoutes(ctx);
            Page page = ctx.newPage();
            page.navigate(PAGE_URL);

            Download download = page.waitForDownload(() -> {
                page.locator("#dl-btn").click();
            });

            assertEquals("report.txt", download.suggestedFilename());

            Path savePath = Paths.get("target", "downloads", "report.txt");
            download.saveAs(savePath);

            assertTrue(Files.readString(savePath).contains("Revenue: $1M"));

            System.out.println("[Test] downloadViaButton passed.");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 4. download.path()：临时文件路径（Context 关闭后删除）
    // ─────────────────────────────────────────────────────────────

    @Test
    void downloadTempPath() throws IOException {
        BrowserContext ctx = browser.newContext();
        setupRoutes(ctx);
        Page page = ctx.newPage();
        page.navigate(PAGE_URL);

        Download download = page.waitForDownload(() -> {
            page.locator("#dl-txt").click();
        });

        // path() 返回浏览器临时下载路径（受 setDownloadsPath 控制）
        Path tempPath = download.path();
        assertNotNull(tempPath);
        assertTrue(Files.exists(tempPath));
        System.out.println("[Test] downloadTempPath: " + tempPath);

        // Context 关闭前，临时文件还在
        String content = Files.readString(tempPath);
        assertEquals("Hello, Playwright!", content);

        ctx.close();

        // Context 关闭后，临时文件可能已被删除（取决于浏览器实现）
        // 注意：saveAs 必须在 ctx.close() 之前调用
        System.out.println("[Test] downloadTempPath passed.");
    }

    // ─────────────────────────────────────────────────────────────
    // 5. 事件式处理：page.onDownload（不知道何时触发下载）
    // ─────────────────────────────────────────────────────────────

    /**
     * page.onDownload 注册事件处理器，适合页面可能在任意时刻触发下载的场景。
     *
     * 注意：事件处理器是异步的，控制流会分叉。
     * 官方建议：如果知道什么触发下载，优先用 waitForDownload。
     * onDownload 适合"不确定何时触发下载"的场景，但需自行等待下载完成。
     *
     * 这里用 waitForDownload 确保下载完成，同时验证 onDownload 事件被触发。
     */
    @Test
    void onDownloadEvent() throws IOException {
        try (BrowserContext ctx = browser.newContext()) {
            setupRoutes(ctx);
            Page page = ctx.newPage();
            page.navigate(PAGE_URL);

            // 用数组"捕获"下载对象（lambda 无法直接 return）
            Download[] captured = new Download[1];
            page.onDownload(d -> {
                captured[0] = d;
                System.out.println("[Event] Download started: " + d.suggestedFilename());
            });

            // 用 waitForDownload 确保下载完成（事件处理器异步触发）
            Download download = page.waitForDownload(() -> {
                page.locator("#dl-txt").click();
            });

            // 验证事件处理器也被触发了
            assertNotNull(captured[0], "onDownload event should have fired");
            assertEquals("hello.txt", captured[0].suggestedFilename());
            assertEquals("hello.txt", download.suggestedFilename());

            download.saveAs(Paths.get("target", "downloads", "event-hello.txt"));

            System.out.println("[Test] onDownloadEvent passed. event captured: "
                    + captured[0].suggestedFilename());
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
