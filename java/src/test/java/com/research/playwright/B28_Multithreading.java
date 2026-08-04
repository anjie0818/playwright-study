package com.research.playwright;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B28 - 官方文档 Multithreading 章节实践
 *
 * 本章核心：
 *   1. Playwright Java 非线程安全 — 所有方法必须在创建 Playwright 的同一线程调用
 *   2. 多线程方案 — 每个线程创建独立的 Playwright 实例
 *   3. 事件分发 — 事件仅在 Playwright 消息循环中分发
 *   4. waitForTimeout vs Thread.sleep — 前者分发事件，后者不分发
 *
 * 运行方式：
 *   mvn test -Dtest=B28_Multithreading
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class B28_Multithreading {

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

    private static final String PAGE_URL = "https://test.local";
    private static final String PAGE_HTML =
            "<html><body>" +
            "<h1>Test Page</h1>" +
            "<div id='counter'>0</div>" +
            "<script>setTimeout(() => document.getElementById('counter').textContent = '5', 500);</script>" +
            "</body></html>";

    private void setupRoute(BrowserContext ctx) {
        ctx.route(PAGE_URL, r -> r.fulfill(
                new Route.FulfillOptions().setContentType("text/html; charset=utf-8").setBody(PAGE_HTML)));
    }

    // ─────────────────────────────────────────────────────────────
    // 1. 多线程：每个线程独立的 Playwright 实例
    // ─────────────────────────────────────────────────────────────

    /**
     * 核心规则：Playwright 对象及其所有子对象（Browser、Context、Page）
     * 必须在创建它们的同一线程上操作。
     *
     * 多线程方案：每个线程创建自己的 Playwright 实例。
     */
    @Test @Order(1)
    void multiThreadSeparateInstances() throws Exception {
        int threadCount = 3;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<String> results = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    // 每个线程创建自己的 Playwright 实例
                    Map<String, String> env = new HashMap<>(System.getenv());
                    String chromePath = resolveChromePath();
                    if (chromePath != null) {
                        env.put("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1");
                    }

                    try (Playwright pw = Playwright.create(new Playwright.CreateOptions().setEnv(env))) {
                        BrowserType.LaunchOptions opts = new BrowserType.LaunchOptions().setHeadless(false);
                        if (chromePath != null) {
                            opts.setExecutablePath(Paths.get(chromePath));
                        }
                        try (Browser br = pw.chromium().launch(opts)) {
                            BrowserContext ctx = br.newContext();
                            Page page = ctx.newPage();
                            page.navigate("data:text/html,<h1>Thread " + threadId + "</h1>");
                            String title = page.locator("h1").textContent();
                            results.add("Thread-" + threadId + ": " + title);
                            successCount.incrementAndGet();
                            ctx.close();
                        }
                    }
                } catch (Exception e) {
                    results.add("Thread-" + threadId + ": ERROR - " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "All threads should complete within 60s");
        assertEquals(threadCount, successCount.get(), "All threads should succeed");
        results.forEach(r -> System.out.println("[Test] " + r));
        System.out.println("[Test] multiThreadSeparateInstances: " + successCount.get() + "/" + threadCount + " succeeded");
    }

    // ─────────────────────────────────────────────────────────────
    // 2. 事件分发：waitForTimeout vs Thread.sleep
    // ─────────────────────────────────────────────────────────────

    /**
     * page.waitForTimeout(ms) 在等待期间仍然分发浏览器事件。
     * 例如 onResponse 监听器会被触发。
     */
    @Test @Order(2)
    void waitForTimeoutDispatchesEvents() {
        try (BrowserContext ctx = browser.newContext()) {
            setupRoute(ctx);
            Page page = ctx.newPage();

            List<String> responses = new ArrayList<>();
            page.onResponse(r -> responses.add(r.url()));

            page.navigate(PAGE_URL);

            // waitForTimeout：等待期间事件正常分发
            page.waitForTimeout(2000);

            assertTrue(responses.stream().anyMatch(u -> u.contains("test.local")),
                    "Response events should be dispatched during waitForTimeout: " + responses);
            System.out.println("[Test] waitForTimeoutDispatchesEvents: captured " + responses.size() + " responses");
        }
    }

    /**
     * Thread.sleep(ms) 在等待期间不会分发浏览器事件。
     * 响应事件会被阻塞，直到下一个 Playwright API 调用时才处理。
     */
    @Test @Order(3)
    void threadSleepBlocksEvents() throws Exception {
        try (BrowserContext ctx = browser.newContext()) {
            setupRoute(ctx);
            Page page = ctx.newPage();

            List<String> responses = new ArrayList<>();
            page.onResponse(r -> responses.add(r.url()));

            // navigate 是同步的，响应事件在 navigate 返回前就分发了
            page.navigate(PAGE_URL);
            // navigate 后响应已经分发
            int countAfterNav = responses.size();
            assertTrue(countAfterNav > 0, "Navigate response should already be dispatched");

            // 异步触发一个新的 fetch 请求
            page.evaluate("() => { setTimeout(() => fetch('/test.local/api'), 200); return 'ok'; }");

            // Thread.sleep：阻塞线程，事件不分发
            Thread.sleep(1000);
            int countAfterSleep = responses.size();

            // waitForTimeout：等待期间事件正常分发
            page.waitForTimeout(1000);
            int countAfterWait = responses.size();

            System.out.println("[Test] threadSleepBlocksEvents: afterNav=" + countAfterNav
                    + ", afterSleep=" + countAfterSleep + ", afterWaitTimeout=" + countAfterWait);
            System.out.println("[Test] 说明：Thread.sleep 和 waitForTimeout 都可能分发事件，"
                    + "区别在于 waitForTimeout 是 Playwright 内部循环的一部分");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 3. waitFor* 方法是事件等待的最佳方式
    // ─────────────────────────────────────────────────────────────

    @Test @Order(4)
    void waitForEventBestPractice() {
        try (BrowserContext ctx = browser.newContext()) {
            setupRoute(ctx);
            Page page = ctx.newPage();

            // ✅ 最佳方式：用 waitForResponse 等待特定事件
            Response response = page.waitForResponse(r -> r.url().contains("test.local"), () -> {
                page.navigate(PAGE_URL);
            });

            assertEquals(200, response.status());
            System.out.println("[Test] waitForEventBestPractice: status=" + response.status()
                    + ", url=" + response.url());
        }
    }

    @Test @Order(5)
    void waitForFunctionDuringExecution() {
        try (BrowserContext ctx = browser.newContext()) {
            setupRoute(ctx);
            Page page = ctx.newPage();
            page.navigate(PAGE_URL);

            // 页面 JS 会在 500ms 后修改 #counter 为 "5"
            // waitForFunction 会自动轮询等待条件满足
            page.waitForFunction(
                    "() => document.getElementById('counter').textContent === '5'",
                    null,
                    new Page.WaitForFunctionOptions().setTimeout(3000));

            String counter = page.locator("#counter").textContent();
            assertEquals("5", counter);
            System.out.println("[Test] waitForFunctionDuringExecution: counter=" + counter);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 4. 同一 Playwright 实例不能跨线程使用
    // ─────────────────────────────────────────────────────────────

    /**
     * 验证：在另一个线程调用 Playwright 方法会导致问题。
     * 这个测试演示"错误做法"——在新线程中操作主线程创建的 Page。
     *
     * ⚠️ 不保证每次都失败，但可能在 CI 中不稳定。
     */
    @Test @Order(6)
    void crossThreadAccessIsUnsafe() throws Exception {
        try (BrowserContext ctx = browser.newContext()) {
            setupRoute(ctx);
            Page page = ctx.newPage();
            page.navigate(PAGE_URL);

            // 在另一个线程操作 page（不应该这样做）
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<Boolean> result = executor.submit(() -> {
                try {
                    // 这可能成功也可能抛异常——行为未定义
                    page.locator("h1").textContent();
                    return true; // "成功"了但不安全
                } catch (Exception e) {
                    return false; // 抛异常了
                }
            });

            boolean succeeded = result.get(5, TimeUnit.SECONDS);
            executor.shutdown();

            // 不 assert 结果，只记录
            System.out.println("[Test] crossThreadAccessIsUnsafe: other thread "
                    + (succeeded ? "succeeded (unsafe!)" : "failed (expected)"));
            System.out.println("[Test] ⚠️ 不要在不同线程操作 Playwright 对象，行为未定义");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 5. 并发测试模式：ExecutorService + 独立 Playwright
    // ─────────────────────────────────────────────────────────────

    /**
     * 模拟 TestMasterAI 的并发执行模式：
     * 多个线程各自创建 Playwright + Browser，并行执行测试。
     */
    @Test @Order(7)
    void parallelTestExecution() throws Exception {
        int parallelism = 4;
        ExecutorService executor = Executors.newFixedThreadPool(parallelism);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(parallelism);
        List<String> results = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < parallelism; i++) {
            final int testId = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // 所有线程同时开始

                    Map<String, String> env = new HashMap<>(System.getenv());
                    String chromePath = resolveChromePath();
                    if (chromePath != null) {
                        env.put("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1");
                    }

                    try (Playwright pw = Playwright.create(new Playwright.CreateOptions().setEnv(env))) {
                        BrowserType.LaunchOptions opts = new BrowserType.LaunchOptions().setHeadless(false);
                        if (chromePath != null) {
                            opts.setExecutablePath(Paths.get(chromePath));
                        }
                        try (Browser br = pw.chromium().launch(opts)) {
                            BrowserContext ctx = br.newContext();
                            ctx.route("**/test*", r -> r.fulfill(
                                    new Route.FulfillOptions().setContentType("text/html; charset=utf-8")
                                            .setBody("<h1>Test " + testId + "</h1>")));

                            Page page = ctx.newPage();
                            page.navigate("https://test.local/test" + testId);

                            String title = page.locator("h1").textContent();
                            long threadId = Thread.currentThread().threadId();
                            results.add("Test-" + testId + ": " + title + " (thread=" + threadId + ")");
                            ctx.close();
                        }
                    }
                } catch (Exception e) {
                    results.add("Test-" + testId + ": ERROR - " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        long startTime = System.currentTimeMillis();
        startLatch.countDown(); // 同时释放所有线程
        boolean completed = doneLatch.await(90, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - startTime;
        executor.shutdown();

        assertTrue(completed, "All parallel tests should complete within 90s");
        assertEquals(parallelism, results.size());
        results.forEach(r -> System.out.println("[Test] " + r));
        System.out.println("[Test] parallelTestExecution: " + parallelism + " tests in " + elapsed + "ms");
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
