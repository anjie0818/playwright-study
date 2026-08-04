package com.research.playwright;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.BoundingBox;
import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B24 - 官方文档 Handles 章节实践
 *
 * 本章核心：
 *   1. JSHandle — 引用页面中任何 JS 对象（evaluateHandle 返回）
 *   2. ElementHandle — 引用 DOM 元素（JSHandle.asElement()），含 boundingBox/getAttribute
 *   3. Locator vs ElementHandle — Locator 每次重新查找元素，ElementHandle 指向固定节点
 *   4. Handle 生命周期 — 阻止 GC，navigate 或 dispose() 后失效
 *   5. Handle 作为参数 — 传递给 evaluate
 *
 *   ⚠️ 官方不推荐用 ElementHandle，推荐用 Locator + Web-first 断言。
 *   Handle 适合需要保持对某个对象的引用的场景。
 *
 * 运行方式：
 *   mvn test -Dtest=B24_Handles
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class B24_Handles {

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
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    private static final String PAGE_URL = "https://test.local";
    private static final String PAGE_HTML =
        "<html><head><style>" +
        "#box { width: 200px; height: 100px; background: red; }" +
        ".highlighted { border: 2px solid yellow; }" +
        "#dynamic { font-size: 16px; }" +
        "</style></head><body>" +
        "<div id='box' class='highlighted box-item'>Click me</div>" +
        "<button id='btn'>Submit</button>" +
        "<div id='dynamic'>Original</div>" +
        "<script>" +
        "document.getElementById('btn').addEventListener('click', () => {" +
        "  document.getElementById('dynamic').textContent = 'Changed';" +
        "});" +
        "window.myData = { items: [1, 2, 3], name: 'test' };" +
        "</script>" +
        "</body></html>";

    private void loadPage(BrowserContext ctx, Page page) {
        ctx.route(PAGE_URL, r -> r.fulfill(
                new Route.FulfillOptions().setContentType("text/html").setBody(PAGE_HTML)));
        page.navigate(PAGE_URL);
    }

    // ─────────────────────────────────────────────────────────────
    // 1. JSHandle：引用页面中的 JS 对象
    // ─────────────────────────────────────────────────────────────

    @Test
    void jsHandleBasic() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            // evaluateHandle 返回 JSHandle（指向页面中的对象引用）
            JSHandle dataHandle = page.evaluateHandle("() => window.myData");

            // 在 Handle 上执行 evaluate，获取属性
            Object name = dataHandle.evaluate("data => data.name");
            assertEquals("test", name);

            // 获取数组长度
            Object length = dataHandle.evaluate("data => data.items.length");
            assertEquals(3, ((Number) length).intValue());

            // 修改页面中的数组
            dataHandle.evaluate("data => data.items.push(4)");

            // 验证修改生效
            Object newLength = dataHandle.evaluate("data => data.items.length");
            assertEquals(4, ((Number) newLength).intValue());
            System.out.println("[Test] jsHandleBasic: name=" + name + ", items.length=" + newLength);
        }
    }

    @Test
    void jsHandleOfWindow() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            // window 是最常用的 JSHandle
            JSHandle windowHandle = page.evaluateHandle("window");

            // 获取 window.location.href
            Object href = windowHandle.evaluate("w => w.location.href");
            assertEquals(PAGE_URL + "/", href);

            System.out.println("[Test] jsHandleOfWindow: href=" + href);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 2. ElementHandle：引用 DOM 元素
    // ─────────────────────────────────────────────────────────────

    @Test
    void elementHandleBoundingBox() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            // waitForSelector 等待元素出现并可见
            ElementHandle box = page.waitForSelector("#box");

            BoundingBox bbox = box.boundingBox();
            assertNotNull(bbox, "Bounding box should not be null");
            assertEquals(204, bbox.width, 1);  // 200px + 2px border * 2
            assertEquals(104, bbox.height, 1); // 100px + 2px border * 2
            System.out.println("[Test] elementHandleBoundingBox: "
                    + bbox.width + "x" + bbox.height + " at (" + bbox.x + "," + bbox.y + ")");
        }
    }

    @Test
    void elementHandleGetAttribute() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            ElementHandle box = page.waitForSelector("#box");

            // getAttribute
            String classAttr = box.getAttribute("class");
            assertTrue(classAttr.contains("highlighted"));

            // innerText / innerHTML / textContent
            String text = box.innerText();
            assertEquals("Click me", text);

            System.out.println("[Test] elementHandleGetAttribute: class=" + classAttr + ", text=" + text);
        }
    }

    @Test
    void elementHandleFromJSHandle() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            // evaluateHandle 返回 JSHandle，asElement() 转为 ElementHandle
            JSHandle jsHandle = page.evaluateHandle("() => document.getElementById('btn')");
            ElementHandle btn = jsHandle.asElement();
            assertNotNull(btn);

            String text = btn.innerText();
            assertEquals("Submit", text);

            // 通过 ElementHandle 执行操作
            btn.click();

            // 验证点击生效（#dynamic 文本变化）
            String dynamic = page.locator("#dynamic").textContent();
            assertEquals("Changed", dynamic);
            System.out.println("[Test] elementHandleFromJSHandle: clicked, dynamic=" + dynamic);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 3. Locator vs ElementHandle（关键对比）
    // ─────────────────────────────────────────────────────────────

    /**
     * ElementHandle 指向固定 DOM 节点。
     * 如果页面重新渲染（如 React），handle 可能指向已过期的节点。
     */
    @Test
    void elementHandlePointsToFixedNode() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            // 获取 ElementHandle（指向此刻的 #dynamic 节点）
            ElementHandle handle = page.waitForSelector("#dynamic");
            String original = handle.innerText();
            assertEquals("Original", original);

            // 点击按钮，#dynamic 文本变为 "Changed"
            page.locator("#btn").click();
            page.waitForFunction("() => document.getElementById('dynamic').textContent === 'Changed'",
                    null, new Page.WaitForFunctionOptions().setTimeout(5000));

            // ⚠️ handle 仍指向同一个 DOM 节点（文本已变）
            String afterClick = handle.innerText();
            assertEquals("Changed", afterClick);

            System.out.println("[Test] elementHandlePointsToFixedNode: original=" + original
                    + ", afterClick=" + afterClick + " (same node, text changed)");
        }
    }

    /**
     * Locator 每次使用时重新查找元素，始终指向最新的匹配元素。
     */
    @Test
    void locatorAlwaysFresh() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            // 获取 Locator（不立即查找）
            Locator locator = page.locator("#dynamic");

            // 每次调用 locator.textContent() 都会重新查找
            String original = locator.textContent();
            assertEquals("Original", original);

            page.locator("#btn").click();
            page.waitForFunction("() => document.getElementById('dynamic').textContent === 'Changed'",
                    null, new Page.WaitForFunctionOptions().setTimeout(5000));

            // Locator 重新查找，得到最新值
            String afterClick = locator.textContent();
            assertEquals("Changed", afterClick);

            System.out.println("[Test] locatorAlwaysFresh: original=" + original
                    + ", afterClick=" + afterClick);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 4. Handle 作为参数传递给 evaluate
    // ─────────────────────────────────────────────────────────────

    @Test
    void handleAsEvaluateArgument() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            // 在页面中创建数组并获取 handle
            JSHandle arrayHandle = page.evaluateHandle(
                "() => { window.myArray = [1]; return window.myArray; }");

            // 用 handle 获取数组长度
            int length = (int) page.evaluate("a => a.length", arrayHandle);
            assertEquals(1, length);

            // 通过 handle 向数组添加元素
            Map<String, Object> arg = new HashMap<>();
            arg.put("myArray", arrayHandle);
            arg.put("newElement", 42);
            page.evaluate("arg => arg.myArray.push(arg.newElement)", arg);

            // 验证
            int newLength = (int) page.evaluate("a => a.length", arrayHandle);
            assertEquals(2, newLength);

            Object lastElement = page.evaluate("a => a[a.length - 1]", arrayHandle);
            assertEquals(42, ((Number) lastElement).intValue());

            System.out.println("[Test] handleAsEvaluateArgument: length=" + newLength
                    + ", last=" + lastElement);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 5. Handle 生命周期：dispose 后不能再使用
    // ─────────────────────────────────────────────────────────────

    @Test
    void handleLifecycleDispose() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            JSHandle handle = page.evaluateHandle("() => window.myData");

            // dispose 前可用
            Object name = handle.evaluate("data => data.name");
            assertEquals("test", name);

            // 释放句柄
            handle.dispose();

            // dispose 后再使用会抛异常
            assertThrows(PlaywrightException.class, () -> {
                handle.evaluate("data => data.name");
            });

            System.out.println("[Test] handleLifecycleDispose: disposed successfully");
        }
    }

    @Test
    void handleInvalidAfterNavigation() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            JSHandle handle = page.evaluateHandle("() => window.myData");
            Object name = handle.evaluate("data => data.name");
            assertEquals("test", name);

            // 导航到新页面（或刷新），旧 handle 失效
            page.navigate("data:text/html,<html><body>new page</body></html>");

            // navigate 后 handle 指向已销毁的页面上下文
            assertThrows(PlaywrightException.class, () -> {
                handle.evaluate("data => data.name");
            });

            System.out.println("[Test] handleInvalidAfterNavigation: handle invalidated after navigation");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 6. querySelector / querySelectorAll（获取 ElementHandle 的替代方式）
    // ─────────────────────────────────────────────────────────────

    @Test
    void querySelectorReturnsHandle() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            // page.querySelector 返回 ElementHandle（等价于 waitForSelector 但不等待）
            ElementHandle box = page.querySelector("#box");
            assertNotNull(box);
            assertEquals("Click me", box.innerText());

            // querySelectorAll 返回 List<ElementHandle>
            List<ElementHandle> allDivs = page.querySelectorAll("div");
            assertTrue(allDivs.size() >= 2); // #box + #dynamic

            System.out.println("[Test] querySelectorReturnsHandle: box=" + box.innerText()
                    + ", divs=" + allDivs.size());
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
