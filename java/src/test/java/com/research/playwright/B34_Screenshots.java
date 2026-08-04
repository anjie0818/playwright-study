package com.research.playwright;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.ScreenshotType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * B34 - Screenshots 章节测试
 * 
 * 测试内容：
 * 1. 基本截图
 * 2. 全页截图
 * 3. 捕获到缓冲区
 * 4. 元素截图
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
public class B34_Screenshots {

    private Playwright playwright;
    private Browser browser;
    private Path screenshotDir;

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

        // 创建截图目录
        screenshotDir = Paths.get("target", "screenshots", "b34");
        try {
            Files.createDirectories(screenshotDir);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create screenshot directory", e);
        }
    }

    @AfterAll
    void afterAll() {
        try { if (browser != null) browser.close(); } catch (Exception ignored) {}
        try { if (playwright != null) playwright.close(); } catch (Exception ignored) {}
    }

    private static final String BASE_URL = "https://test.local";

    private void setupRoutes(BrowserContext ctx) {
        ctx.route("**/*", route -> {
            String url = route.request().url();
            
            if (url.contains("/long")) {
                // 长页面，需要滚动
                route.fulfill(new Route.FulfillOptions()
                    .setContentType("text/html; charset=utf-8")
                    .setBody("<html><body style='height: 3000px; background: linear-gradient(to bottom, red, yellow, green, blue);'>" +
                        "<div style='position: absolute; top: 100px; left: 50px; font-size: 24px;'>Top Section</div>" +
                        "<div style='position: absolute; top: 1500px; left: 50px; font-size: 24px;'>Middle Section</div>" +
                        "<div style='position: absolute; top: 2800px; left: 50px; font-size: 24px;'>Bottom Section</div>" +
                        "</body></html>"));
            } else if (url.contains("/elements")) {
                // 包含多个元素的页面
                route.fulfill(new Route.FulfillOptions()
                    .setContentType("text/html; charset=utf-8")
                    .setBody("<html><body>" +
                        "<header style='background: navy; color: white; padding: 20px;'><h1>Site Header</h1></header>" +
                        "<main style='padding: 20px;'>" +
                        "  <div class='card' style='border: 2px solid #ccc; padding: 15px; margin: 10px 0; background: #f9f9f9;'>" +
                        "    <h2>Card 1</h2><p>This is the first card</p>" +
                        "  </div>" +
                        "  <div class='card' style='border: 2px solid #ccc; padding: 15px; margin: 10px 0; background: #f9f9f9;'>" +
                        "    <h2>Card 2</h2><p>This is the second card</p>" +
                        "  </div>" +
                        "</main>" +
                        "<footer style='background: #333; color: white; padding: 20px;'>Footer Content</footer>" +
                        "</body></html>"));
            } else {
                route.fulfill(new Route.FulfillOptions()
                    .setContentType("text/html; charset=utf-8")
                    .setBody("<html><body><h1>Default Page</h1></body></html>"));
            }
        });
    }

    // ─────────────────────────────────────────────────────────────
    // 1. 基本截图
    // ─────────────────────────────────────────────────────────────

    @Test @Order(1)
    void basicScreenshot() {
        try (BrowserContext ctx = browser.newContext()) {
            setupRoutes(ctx);
            Page page = ctx.newPage();

            page.navigate(BASE_URL + "/elements");

            Path screenshotPath = screenshotDir.resolve("basic.png");
            page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath));

            assertTrue(Files.exists(screenshotPath), "Screenshot should exist: " + screenshotPath);
            System.out.println("[Test] basicScreenshot: saved to " + screenshotPath);
        }
    }

    @Test @Order(2)
    void screenshotWithOptions() throws IOException {
        try (BrowserContext ctx = browser.newContext()) {
            setupRoutes(ctx);
            Page page = ctx.newPage();

            page.navigate(BASE_URL + "/elements");

            // 使用 JPEG 格式和质量参数
            Path jpegPath = screenshotDir.resolve("options.jpeg");
            page.screenshot(new Page.ScreenshotOptions()
                .setPath(jpegPath)
                .setType(ScreenshotType.JPEG)
                .setQuality(50));  // 低质量，文件更小

            assertTrue(Files.exists(jpegPath));
            
            // PNG 文件通常比低质量 JPEG 大
            Path pngPath = screenshotDir.resolve("options.png");
            page.screenshot(new Page.ScreenshotOptions().setPath(pngPath));
            assertTrue(Files.exists(pngPath));

            long jpegSize = Files.size(jpegPath);
            long pngSize = Files.size(pngPath);
            
            System.out.println("[Test] screenshotWithOptions: JPEG=" + jpegSize + " bytes, PNG=" + pngSize + " bytes");
            // JPEG 和 PNG 都能正常生成即可，大小取决于图片内容
            assertTrue(jpegSize > 0 && pngSize > 0, "Both files should have content");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 2. 全页截图
    // ─────────────────────────────────────────────────────────────

    @Test @Order(3)
    void fullPageScreenshot() throws IOException {
        try (BrowserContext ctx = browser.newContext()) {
            setupRoutes(ctx);
            Page page = ctx.newPage();

            page.navigate(BASE_URL + "/long");

            // 全页截图（包括不可见部分）
            Path fullPath = screenshotDir.resolve("fullpage.png");
            page.screenshot(new Page.ScreenshotOptions()
                .setPath(fullPath)
                .setFullPage(true));

            assertTrue(Files.exists(fullPath));
            
            // 普通截图（只有可视区域）
            Path viewportPath = screenshotDir.resolve("viewport.png");
            page.screenshot(new Page.ScreenshotOptions().setPath(viewportPath));

            long fullSize = Files.size(fullPath);
            long viewportSize = Files.size(viewportPath);

            System.out.println("[Test] fullPageScreenshot: full=" + fullSize + " bytes, viewport=" + viewportSize + " bytes");
            assertTrue(fullSize > viewportSize, "Full page screenshot should be larger than viewport");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 3. 捕获到缓冲区
    // ─────────────────────────────────────────────────────────────

    @Test @Order(4)
    void screenshotToBuffer() throws IOException {
        try (BrowserContext ctx = browser.newContext()) {
            setupRoutes(ctx);
            Page page = ctx.newPage();

            page.navigate(BASE_URL + "/elements");

            // 捕获到内存缓冲区
            byte[] buffer = page.screenshot();

            assertNotNull(buffer);
            assertTrue(buffer.length > 0);

            // 转换为 Base64
            String base64 = Base64.getEncoder().encodeToString(buffer);
            assertNotNull(base64);
            assertTrue(base64.length() > 0);

            System.out.println("[Test] screenshotToBuffer: buffer=" + buffer.length + " bytes, base64=" + base64.length() + " chars");

            // 可以保存到文件
            Path savedPath = screenshotDir.resolve("from-buffer.png");
            Files.write(savedPath, buffer);
            assertTrue(Files.exists(savedPath));
        }
    }

    @Test @Order(5)
    void screenshotToBufferWithOptions() {
        try (BrowserContext ctx = browser.newContext()) {
            setupRoutes(ctx);
            Page page = ctx.newPage();

            page.navigate(BASE_URL + "/long");

            // 捕获全页到缓冲区
            byte[] fullBuffer = page.screenshot(new Page.ScreenshotOptions().setFullPage(true));

            // 捕获可视区域到缓冲区
            byte[] viewportBuffer = page.screenshot();

            assertTrue(fullBuffer.length > viewportBuffer.length, 
                "Full page buffer should be larger");

            System.out.println("[Test] screenshotToBufferWithOptions: full=" + fullBuffer.length + 
                " bytes, viewport=" + viewportBuffer.length + " bytes");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 4. 元素截图
    // ─────────────────────────────────────────────────────────────

    @Test @Order(6)
    void elementScreenshot() {
        try (BrowserContext ctx = browser.newContext()) {
            setupRoutes(ctx);
            Page page = ctx.newPage();

            page.navigate(BASE_URL + "/elements");

            // 对单个元素截图
            Locator header = page.locator("header");
            Path headerPath = screenshotDir.resolve("element-header.png");
            header.screenshot(new Locator.ScreenshotOptions().setPath(headerPath));

            Locator card = page.locator(".card").first();
            Path cardPath = screenshotDir.resolve("element-card.png");
            card.screenshot(new Locator.ScreenshotOptions().setPath(cardPath));

            Locator footer = page.locator("footer");
            Path footerPath = screenshotDir.resolve("element-footer.png");
            footer.screenshot(new Locator.ScreenshotOptions().setPath(footerPath));

            assertTrue(Files.exists(headerPath));
            assertTrue(Files.exists(cardPath));
            assertTrue(Files.exists(footerPath));

            System.out.println("[Test] elementScreenshot: captured header, card, and footer");
        }
    }

    @Test @Order(7)
    void elementScreenshotAfterInteraction() {
        try (BrowserContext ctx = browser.newContext()) {
            setupRoutes(ctx);
            Page page = ctx.newPage();

            page.navigate(BASE_URL + "/elements");

            // 交互前截图
            Locator card = page.locator(".card").first();
            Path beforePath = screenshotDir.resolve("element-before.png");
            card.screenshot(new Locator.ScreenshotOptions().setPath(beforePath));

            // 修改元素
            card.evaluate("el => el.style.border = '5px solid red'");

            // 交互后截图
            Path afterPath = screenshotDir.resolve("element-after.png");
            card.screenshot(new Locator.ScreenshotOptions().setPath(afterPath));

            assertTrue(Files.exists(beforePath));
            assertTrue(Files.exists(afterPath));

            System.out.println("[Test] elementScreenshotAfterInteraction: captured before and after states");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 5. 裁剪区域截图
    // ─────────────────────────────────────────────────────────────

    @Test @Order(8)
    void screenshotWithClip() {
        try (BrowserContext ctx = browser.newContext()) {
            setupRoutes(ctx);
            Page page = ctx.newPage();

            page.navigate(BASE_URL + "/elements");

            // 只截取页面的特定区域
            Path clipPath = screenshotDir.resolve("clipped.png");
            page.screenshot(new Page.ScreenshotOptions()
                .setPath(clipPath)
                .setClip(0, 0, 400, 300));  // x, y, width, height

            assertTrue(Files.exists(clipPath));
            System.out.println("[Test] screenshotWithClip: captured 400x300 region");
        }
    }

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
