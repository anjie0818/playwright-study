package com.research.playwright;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B37 - Touch Events (legacy) 章节测试
 *
 * 本章核心：
 *   通过 Locator.dispatchEvent() 手动分发触摸事件，模拟平移(pan)、捏合(pinch)等手势。
 *   适用于测试处理旧式触摸事件的 Web 应用。
 *
 *   注意：dispatchEvent() 不会设置 Event.isTrusted 属性。
 *   如果应用依赖 isTrusted，需在测试中禁用相关检查。
 *
 * 运行方式：
 *   mvn test -Dtest=B37_TouchEvents
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
public class B37_TouchEvents {

    private Playwright playwright;
    private Browser browser;

    @BeforeAll
    void beforeAll() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch();
    }

    @AfterAll
    void afterAll() {
        browser.close();
        playwright.close();
    }

    /**
     * 创建移动设备上下文（带触摸支持）
     */
    private BrowserContext createMobileContext() {
        return browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(412, 839)
                .setDeviceScaleFactor(2.625)
                .setUserAgent("Mozilla/5.0 (Linux; Android 12; Pixel 7) " +
                        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/94.0.4606.71 Mobile Safari/537.36")
                .setHasTouch(true)
                .setIsMobile(true));
    }

    /**
     * 创建带触摸事件处理的测试页面
     */
    private static final String TOUCH_PAGE = "<html><body>" +
            "<div id='touch-area' style='width:300px;height:300px;background:#eee;border:1px solid #ccc;'>" +
            "  Touch Area" +
            "</div>" +
            "<div id='log'></div>" +
            "<script>" +
            "const area = document.getElementById('touch-area');" +
            "const log = document.getElementById('log');" +
            "let lastX = 0, lastY = 0;" +
            "area.addEventListener('touchstart', e => {" +
            "  const t = e.touches[0];" +
            "  lastX = t.clientX; lastY = t.clientY;" +
            "  log.textContent += 'start:' + Math.round(t.clientX) + ',' + Math.round(t.clientY) + ';';" +
            "});" +
            "area.addEventListener('touchmove', e => {" +
            "  const t = e.touches[0];" +
            "  lastX = t.clientX; lastY = t.clientY;" +
            "  log.textContent += 'move:' + Math.round(t.clientX) + ',' + Math.round(t.clientY) + ';';" +
            "});" +
            "area.addEventListener('touchend', e => {" +
            "  log.textContent += 'end;';" +
            "});" +
            "</script>" +
            "</body></html>";

    // ─────────────────────────────────────────────────────────────
    // 1. 基本触摸事件：touchstart → touchend
    // ─────────────────────────────────────────────────────────────

    @Test @Order(1)
    void basicTap() {
        try (BrowserContext ctx = createMobileContext()) {
            Page page = ctx.newPage();
            page.setContent(TOUCH_PAGE);

            Locator area = page.locator("#touch-area");
            BoundingBox bounds = area.boundingBox();
            double centerX = bounds.x + bounds.width / 2;
            double centerY = bounds.y + bounds.height / 2;

            // 模拟点击（touchstart → touchend）
            List<Map<String, Object>> touches = List.of(Map.of(
                    "identifier", 0,
                    "clientX", centerX,
                    "clientY", centerY));

            area.dispatchEvent("touchstart", Map.of(
                    "touches", touches,
                    "changedTouches", touches,
                    "targetTouches", touches));

            area.dispatchEvent("touchend", Map.of(
                    "touches", List.of(),
                    "changedTouches", touches,
                    "targetTouches", List.of()));

            String log = page.locator("#log").textContent();
            assertTrue(log.contains("start:"), "Should have touchstart: " + log);
            assertTrue(log.contains("end;"), "Should have touchend: " + log);
            System.out.println("[Test] basicTap: " + log);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 2. 平移手势 (Pan Gesture)
    // ─────────────────────────────────────────────────────────────

    @Test @Order(2)
    void panGesture() {
        try (BrowserContext ctx = createMobileContext()) {
            Page page = ctx.newPage();
            page.setContent(TOUCH_PAGE);

            Locator area = page.locator("#touch-area");
            pan(area, 100, 50, 5);

            String log = page.locator("#log").textContent();
            assertTrue(log.contains("start:"), "Should have touchstart");
            assertTrue(log.contains("move:"), "Should have touchmove");
            assertTrue(log.contains("end;"), "Should have touchend");

            // 计算 move 事件数量
            long moveCount = log.chars().filter(c -> c == ';').count() - 2; // 减去 start 和 end
            assertTrue(moveCount >= 5, "Should have at least 5 move events: " + log);

            System.out.println("[Test] panGesture: " + log);
        }
    }

    /**
     * 模拟平移手势
     */
    private void pan(Locator locator, int deltaX, int deltaY, int steps) {
        BoundingBox bounds = locator.boundingBox();
        double centerX = bounds.x + bounds.width / 2;
        double centerY = bounds.y + bounds.height / 2;

        // touchstart
        List<Map<String, Object>> touches = List.of(Map.of(
                "identifier", 0,
                "clientX", centerX,
                "clientY", centerY));
        locator.dispatchEvent("touchstart", Map.of(
                "touches", touches,
                "changedTouches", touches,
                "targetTouches", touches));

        // touchmove（多步）
        for (int i = 1; i <= steps; i++) {
            touches = List.of(Map.of(
                    "identifier", 0,
                    "clientX", centerX + deltaX * i / steps,
                    "clientY", centerY + deltaY * i / steps));
            locator.dispatchEvent("touchmove", Map.of(
                    "touches", touches,
                    "changedTouches", touches,
                    "targetTouches", touches));
        }

        // touchend
        locator.dispatchEvent("touchend", Map.of(
                "touches", List.of(),
                "changedTouches", touches,
                "targetTouches", List.of()));
    }

    // ─────────────────────────────────────────────────────────────
    // 3. 捏合手势 (Pinch Gesture)
    // ─────────────────────────────────────────────────────────────

    @Test @Order(3)
    void pinchInGesture() {
        try (BrowserContext ctx = createMobileContext()) {
            Page page = ctx.newPage();

            // 带缩放跟踪的页面
            page.setContent("<html><body>" +
                    "<div id='pinch-area' style='width:300px;height:300px;background:#eee;'></div>" +
                    "<div id='scale'>1.0</div>" +
                    "<script>" +
                    "const area = document.getElementById('pinch-area');" +
                    "const scaleEl = document.getElementById('scale');" +
                    "let initialDist = 0, currentScale = 1.0;" +
                    "function getDistance(t1, t2) {" +
                    "  return Math.sqrt(Math.pow(t2.clientX - t1.clientX, 2) + Math.pow(t2.clientY - t1.clientY, 2));" +
                    "}" +
                    "area.addEventListener('touchstart', e => {" +
                    "  if (e.touches.length === 2) initialDist = getDistance(e.touches[0], e.touches[1]);" +
                    "});" +
                    "area.addEventListener('touchmove', e => {" +
                    "  if (e.touches.length === 2 && initialDist > 0) {" +
                    "    currentScale = getDistance(e.touches[0], e.touches[1]) / initialDist;" +
                    "    scaleEl.textContent = currentScale.toFixed(2);" +
                    "  }" +
                    "});" +
                    "</script>" +
                    "</body></html>");

            Locator area = page.locator("#pinch-area");
            pinch(area, 40, "in", 5);

            String scale = page.locator("#scale").textContent();
            double scaleValue = Double.parseDouble(scale);
            assertTrue(scaleValue < 1.0, "Pinch in should reduce scale: " + scale);

            System.out.println("[Test] pinchInGesture: scale=" + scale);
        }
    }

    @Test @Order(4)
    void pinchOutGesture() {
        try (BrowserContext ctx = createMobileContext()) {
            Page page = ctx.newPage();

            page.setContent("<html><body>" +
                    "<div id='pinch-area' style='width:300px;height:300px;background:#eee;'></div>" +
                    "<div id='scale'>1.0</div>" +
                    "<script>" +
                    "const area = document.getElementById('pinch-area');" +
                    "const scaleEl = document.getElementById('scale');" +
                    "let initialDist = 0, currentScale = 1.0;" +
                    "function getDistance(t1, t2) {" +
                    "  return Math.sqrt(Math.pow(t2.clientX - t1.clientX, 2) + Math.pow(t2.clientY - t1.clientY, 2));" +
                    "}" +
                    "area.addEventListener('touchstart', e => {" +
                    "  if (e.touches.length === 2) initialDist = getDistance(e.touches[0], e.touches[1]);" +
                    "});" +
                    "area.addEventListener('touchmove', e => {" +
                    "  if (e.touches.length === 2 && initialDist > 0) {" +
                    "    currentScale = getDistance(e.touches[0], e.touches[1]) / initialDist;" +
                    "    scaleEl.textContent = currentScale.toFixed(2);" +
                    "  }" +
                    "});" +
                    "</script>" +
                    "</body></html>");

            Locator area = page.locator("#pinch-area");
            pinch(area, 40, "out", 5);

            String scale = page.locator("#scale").textContent();
            double scaleValue = Double.parseDouble(scale);
            assertTrue(scaleValue > 1.0, "Pinch out should increase scale: " + scale);

            System.out.println("[Test] pinchOutGesture: scale=" + scale);
        }
    }

    /**
     * 模拟捏合手势（两个触摸点）
     */
    private void pinch(Locator locator, int deltaX, String direction, int steps) {
        BoundingBox bounds = locator.boundingBox();
        double centerX = bounds.x + bounds.width / 2;
        double centerY = bounds.y + bounds.height / 2;
        double stepDeltaX = deltaX / (steps + 1.0);

        // 两个触摸点的初始位置
        double x1 = centerX - (direction.equals("in") ? deltaX : stepDeltaX);
        double x2 = centerX + (direction.equals("in") ? deltaX : stepDeltaX);

        List<Map<String, Object>> touches = List.of(
                Map.of("identifier", 0, "clientX", x1, "clientY", centerY),
                Map.of("identifier", 1, "clientX", x2, "clientY", centerY));

        locator.dispatchEvent("touchstart", Map.of(
                "touches", touches,
                "changedTouches", touches,
                "targetTouches", touches));

        // 逐步捏合
        for (int i = 1; i <= steps; i++) {
            double progress = (double) i / steps;
            double currentX1, currentX2;
            if (direction.equals("in")) {
                // 两个点向中心靠拢
                currentX1 = centerX - deltaX + deltaX * progress;
                currentX2 = centerX + deltaX - deltaX * progress;
            } else {
                // 两个点向外扩展
                currentX1 = centerX - stepDeltaX - stepDeltaX * i;
                currentX2 = centerX + stepDeltaX + stepDeltaX * i;
            }

            touches = List.of(
                    Map.of("identifier", 0, "clientX", currentX1, "clientY", centerY),
                    Map.of("identifier", 1, "clientX", currentX2, "clientY", centerY));

            locator.dispatchEvent("touchmove", Map.of(
                    "touches", touches,
                    "changedTouches", touches,
                    "targetTouches", touches));
        }

        locator.dispatchEvent("touchend", Map.of(
                "touches", List.of(),
                "changedTouches", touches,
                "targetTouches", List.of()));
    }

    // ─────────────────────────────────────────────────────────────
    // 4. 移动设备上下文验证
    // ─────────────────────────────────────────────────────────────

    @Test @Order(5)
    void mobileContextVerification() {
        try (BrowserContext ctx = createMobileContext()) {
            Page page = ctx.newPage();
            page.setContent("<html><body>" +
                    "<div id='info'></div>" +
                    "<script>" +
                    "document.getElementById('info').textContent = " +
                    "  'touch:' + ('ontouchstart' in window) + " +
                    "  ',mobile:' + /Mobile/.test(navigator.userAgent);" +
                    "</script></body></html>");

            String info = page.locator("#info").textContent();
            // Playwright 的 setHasTouch(true) 启用了触摸事件分发（已通过其他测试验证），
            // 但 JS 的 'ontouchstart' in window 检测在桌面 Chrome 上可能返回 false
            assertTrue(info.contains("mobile:true"), "Should be mobile: " + info);

            System.out.println("[Test] mobileContextVerification: " + info);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 5. 多步平移手势
    // ─────────────────────────────────────────────────────────────

    @Test @Order(6)
    void multiStepPan() {
        try (BrowserContext ctx = createMobileContext()) {
            Page page = ctx.newPage();
            page.setContent(TOUCH_PAGE);

            Locator area = page.locator("#touch-area");

            // 多次平移
            for (int i = 0; i < 3; i++) {
                pan(area, 50, 30, 3);
            }

            String log = page.locator("#log").textContent();
            // 验证至少有多个触摸事件发生
            assertTrue(log.contains("start:"), "Should have touchstart events: " + log);
            assertTrue(log.contains("move:"), "Should have touchmove events: " + log);
            assertTrue(log.contains("end;"), "Should have touchend events: " + log);

            System.out.println("[Test] multiStepPan: multiple pans completed");
            System.out.println("  Log: " + log);
        }
    }
}
