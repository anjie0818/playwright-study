package com.research.playwright;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import java.nio.file.*;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B38 - Videos 章节测试
 * 
 * 本章核心：
 *   Playwright 可以为测试录制视频，视频在浏览器上下文关闭后保存。
 * 
 * 运行方式：
 *   mvn test -Dtest=B38_Videos
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
public class B38_Videos {

    private Playwright playwright;
    private Browser browser;
    private Path videoDir;

    @BeforeAll
    void beforeAll() throws Exception {
        playwright = Playwright.create();
        browser = playwright.chromium().launch();
        
        // 创建视频目录
        videoDir = Paths.get("target/videos");
        Files.createDirectories(videoDir);
    }

    @AfterAll
    void afterAll() {
        browser.close();
        playwright.close();
    }

    // ─────────────────────────────────────────────────────────────
    // 1. 基本视频录制
    // ─────────────────────────────────────────────────────────────

    @Test @Order(1)
    void basicVideoRecording() throws Exception {
        BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setRecordVideoDir(videoDir));

        Page page = context.newPage();
        page.navigate("https://example.com");
        page.locator("h1").click();
        page.waitForTimeout(1000);

        // 视频在上下文关闭后才保存
        context.close();

        // 验证视频文件已生成
        List<Path> videoFiles = Files.list(videoDir)
                .filter(p -> p.toString().endsWith(".webm"))
                .collect(Collectors.toList());

        assertFalse(videoFiles.isEmpty(), "Video file should be created");
        assertTrue(Files.size(videoFiles.get(0)) > 0, "Video file should not be empty");

        System.out.println("[Test] basicVideoRecording: " + videoFiles.get(0).getFileName() +
                " (" + Files.size(videoFiles.get(0)) + " bytes)");
    }

    // ─────────────────────────────────────────────────────────────
    // 2. 自定义视频尺寸
    // ─────────────────────────────────────────────────────────────

    @Test @Order(2)
    void customVideoSize() throws Exception {
        BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setRecordVideoDir(videoDir)
                .setRecordVideoSize(640, 480));

        Page page = context.newPage();
        page.navigate("https://example.com");
        page.locator("h1").click();
        page.waitForTimeout(1000);

        context.close();

        List<Path> videoFiles = Files.list(videoDir)
                .filter(p -> p.toString().endsWith(".webm"))
                .collect(Collectors.toList());

        assertFalse(videoFiles.isEmpty(), "Video file should be created");

        System.out.println("[Test] customVideoSize: 640x480 video recorded");
    }

    // ─────────────────────────────────────────────────────────────
    // 3. 访问视频路径
    // ─────────────────────────────────────────────────────────────

    @Test @Order(3)
    void accessVideoPath() throws Exception {
        BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setRecordVideoDir(videoDir));

        Page page = context.newPage();
        page.navigate("https://example.com");
        page.locator("h1").click();
        page.waitForTimeout(500);

        // 获取视频对象（在关闭前可以获取）
        Video video = page.video();
        assertNotNull(video, "Video object should be accessible");

        context.close();

        // 关闭后获取路径
        Path videoPath = video.path();
        assertTrue(Files.exists(videoPath), "Video file should exist at path");
        assertTrue(Files.size(videoPath) > 0, "Video file should not be empty");

        System.out.println("[Test] accessVideoPath: " + videoPath);
    }

    // ─────────────────────────────────────────────────────────────
    // 4. 多页面场景
    // ─────────────────────────────────────────────────────────────

    @Test @Order(4)
    void multiPageVideos() throws Exception {
        // 清空目录
        Files.list(videoDir)
                .filter(p -> p.toString().endsWith(".webm"))
                .forEach(p -> {
                    try { Files.delete(p); } catch (Exception e) {}
                });

        BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setRecordVideoDir(videoDir));

        // 创建多个页面
        Page page1 = context.newPage();
        page1.navigate("https://example.com");
        page1.locator("h1").click();
        page1.waitForTimeout(500);

        Page page2 = context.newPage();
        page2.navigate("https://example.org");
        page2.locator("h1").click();
        page2.waitForTimeout(500);

        // 每个页面有自己的视频
        Video video1 = page1.video();
        Video video2 = page2.video();
        assertNotNull(video1, "Page 1 should have video");
        assertNotNull(video2, "Page 2 should have video");

        context.close();

        // 验证两个视频文件都存在
        Path path1 = video1.path();
        Path path2 = video2.path();
        assertNotEquals(path1, path2, "Each page should have its own video");
        assertTrue(Files.exists(path1), "Video 1 should exist");
        assertTrue(Files.exists(path2), "Video 2 should exist");

        // 统计视频文件数量
        long videoCount = Files.list(videoDir)
                .filter(p -> p.toString().endsWith(".webm"))
                .count();
        assertEquals(2, videoCount, "Should have 2 video files");

        System.out.println("[Test] multiPageVideos: " + videoCount + " videos created");
    }

    // ─────────────────────────────────────────────────────────────
    // 5. 视口与视频尺寸关系
    // ─────────────────────────────────────────────────────────────

    @Test @Order(5)
    void viewportAndVideoSize() throws Exception {
        // 视口 1280x720，视频 800x600
        BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(1280, 720)
                .setRecordVideoDir(videoDir)
                .setRecordVideoSize(800, 600));

        Page page = context.newPage();
        page.navigate("https://example.com");
        page.locator("h1").click();
        page.waitForTimeout(500);

        context.close();

        List<Path> videoFiles = Files.list(videoDir)
                .filter(p -> p.toString().endsWith(".webm"))
                .collect(Collectors.toList());

        assertFalse(videoFiles.isEmpty(), "Video file should be created");

        System.out.println("[Test] viewportAndVideoSize: viewport 1280x720, video 800x600");
    }

    // ─────────────────────────────────────────────────────────────
    // 6. 不录制视频
    // ─────────────────────────────────────────────────────────────

    @Test @Order(6)
    void noVideoRecording() throws Exception {
        // 清空目录
        long beforeCount = Files.list(videoDir)
                .filter(p -> p.toString().endsWith(".webm"))
                .count();

        // 不设置 recordVideoDir
        BrowserContext context = browser.newContext();

        Page page = context.newPage();
        page.navigate("https://example.com");
        page.locator("h1").click();
        page.waitForTimeout(500);

        // 没有视频对象
        Video video = page.video();
        assertNull(video, "Video should be null when not recording");

        context.close();

        // 验证没有新增视频文件
        long afterCount = Files.list(videoDir)
                .filter(p -> p.toString().endsWith(".webm"))
                .count();
        assertEquals(beforeCount, afterCount, "No new video files should be created");

        System.out.println("[Test] noVideoRecording: no video recorded");
    }
}
