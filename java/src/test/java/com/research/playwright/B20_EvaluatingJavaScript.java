package com.research.playwright;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B20 - 官方文档 Evaluating JavaScript 章节实践
 *
 * 本章核心：
 *   1. page.evaluate(js) — 在浏览器环境执行 JS，返回结果到 Playwright 环境
 *   2. 环境隔离 — 测试变量不能直接在页面中使用，必须作为参数传递
 *   3. 评估参数 — 传递基本类型、数组、对象、JSHandle
 *   4. addInitScript — 页面加载前注入 JS（mock、测试数据等）
 *
 * 运行方式：
 *   mvn test -Dtest=B20_EvaluatingJavaScript
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class B20_EvaluatingJavaScript {

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
        "<html><head></head><body>" +
        "<button id='btn1'>Click Me</button>" +
        "<button id='btn2'>Submit</button>" +
        "<div id='counter'>0</div>" +
        "<script>" +
        "window.clickCount = 0;" +
        "document.getElementById('btn1').addEventListener('click', () => {" +
        "  window.clickCount++;" +
        "  document.getElementById('counter').textContent = window.clickCount;" +
        "});" +
        "</script>" +
        "</body></html>";

    private void loadPage(BrowserContext ctx, Page page) {
        ctx.route(PAGE_URL, r -> r.fulfill(
                new Route.FulfillOptions().setContentType("text/html").setBody(PAGE_HTML)));
        page.navigate(PAGE_URL);
    }

    // ─────────────────────────────────────────────────────────────
    // 1. 基本 evaluate：返回不同类型
    // ─────────────────────────────────────────────────────────────

    @Test
    void evaluateReturnsString() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            String href = (String) page.evaluate("document.location.href");
            assertEquals(PAGE_URL + "/", href);
            System.out.println("[Test] evaluateReturnsString: " + href);
        }
    }

    @Test
    void evaluateReturnsNumber() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            Object result = page.evaluate("() => 1 + 2");
            assertEquals(3, ((Number) result).intValue());
            System.out.println("[Test] evaluateReturnsNumber: 1 + 2 = " + result);
        }
    }

    @Test
    void evaluateReturnsBoolean() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            Object result = page.evaluate("() => document.getElementById('btn1') !== null");
            assertTrue((Boolean) result);
            System.out.println("[Test] evaluateReturnsBoolean: button exists = " + result);
        }
    }

    @Test
    void evaluateReturnsObject() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) page.evaluate(
                "() => ({ name: 'Playwright', version: 56, openSource: true })");

            assertEquals("Playwright", result.get("name"));
            assertEquals(56, ((Number) result.get("version")).intValue());
            assertEquals(true, result.get("openSource"));
            System.out.println("[Test] evaluateReturnsObject: " + result);
        }
    }

    @Test
    void evaluateReturnsArray() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            @SuppressWarnings("unchecked")
            List<Object> result = (List<Object>) page.evaluate(
                "() => [1, 'two', true, null]");

            assertEquals(4, result.size());
            assertEquals(1, ((Number) result.get(0)).intValue());
            assertEquals("two", result.get(1));
            System.out.println("[Test] evaluateReturnsArray: " + result);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 2. 异步 evaluate：自动 await Promise
    // ─────────────────────────────────────────────────────────────

    @Test
    void evaluateAsyncFunction() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            // evaluate 自动等待 async 函数完成
            Object result = page.evaluate(
                "async () => {" +
                "  await new Promise(r => setTimeout(r, 100));" +
                "  return 'done';" +
                "}");

            assertEquals("done", result);
            System.out.println("[Test] evaluateAsyncFunction: " + result);
        }
    }

    @Test
    void evaluatePromiseResolve() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            // 直接返回 Promise，evaluate 会自动 await
            Object result = page.evaluate("Promise.resolve(42)");
            assertEquals(42, ((Number) result).intValue());
            System.out.println("[Test] evaluatePromiseResolve: " + result);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 3. 传递参数：基本类型、数组、对象
    // ─────────────────────────────────────────────────────────────

    @Test
    void passPrimitiveArgument() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            // 传递基本类型
            Object result = page.evaluate("num => num * 2", 21);
            assertEquals(42, ((Number) result).intValue());
            System.out.println("[Test] passPrimitiveArgument: 21 * 2 = " + result);
        }
    }

    @Test
    void passArrayArgument() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            // 传递数组
            Object result = page.evaluate(
                "array => array.reduce((a, b) => a + b, 0)",
                Arrays.asList(1, 2, 3, 4, 5));

            assertEquals(15, ((Number) result).intValue());
            System.out.println("[Test] passArrayArgument: sum = " + result);
        }
    }

    @Test
    void passObjectArgument() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            // 传递对象
            Map<String, Object> data = new HashMap<>();
            data.put("name", "Alice");
            data.put("age", 30);

            Object result = page.evaluate(
                "obj => obj.name + ' is ' + obj.age + ' years old'",
                data);

            assertEquals("Alice is 30 years old", result);
            System.out.println("[Test] passObjectArgument: " + result);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 4. JSHandle 作为参数
    // ─────────────────────────────────────────────────────────────

    @Test
    void passJSHandleAsArgument() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            // evaluateHandle 返回 JSHandle（页面中 JS 对象的引用）
            JSHandle button = page.evaluateHandle("() => document.getElementById('btn1')");

            // 将 Handle 作为参数传递给下一个 evaluate
            String text = (String) page.evaluate(
                "button => button.textContent", button);

            assertEquals("Click Me", text);
            System.out.println("[Test] passJSHandleAsArgument: " + text);
        }
    }

    @Test
    void passMultipleHandlesInObject() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            JSHandle btn1 = page.evaluateHandle("() => document.getElementById('btn1')");
            JSHandle btn2 = page.evaluateHandle("() => document.getElementById('btn2')");

            // 多个 Handle 放在对象中传递
            Map<String, JSHandle> arg = new HashMap<>();
            arg.put("b1", btn1);
            arg.put("b2", btn2);

            String result = (String) page.evaluate(
                "o => o.b1.textContent + ' + ' + o.b2.textContent", arg);

            assertEquals("Click Me + Submit", result);
            System.out.println("[Test] passMultipleHandlesInObject: " + result);
        }
    }

    @Test
    void passHandlesInArray() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            JSHandle btn1 = page.evaluateHandle("() => document.getElementById('btn1')");
            JSHandle btn2 = page.evaluateHandle("() => document.getElementById('btn2')");

            // 数组解构语法（注意括号）
            String result = (String) page.evaluate(
                "([b1, b2]) => b1.textContent + ' | ' + b2.textContent",
                Arrays.asList(btn1, btn2));

            assertEquals("Click Me | Submit", result);
            System.out.println("[Test] passHandlesInArray: " + result);
        }
    }

    @Test
    void mixHandlesAndPrimitives() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            JSHandle btn1 = page.evaluateHandle("() => document.getElementById('btn1')");
            JSHandle btn2 = page.evaluateHandle("() => document.getElementById('btn2')");

            // Handle + 基本类型混合
            Map<String, Object> arg = new HashMap<>();
            arg.put("button1", btn1);
            arg.put("list", Arrays.asList(btn2));
            arg.put("separator", " + ");

            String result = (String) page.evaluate(
                "x => x.button1.textContent + x.separator + x.list[0].textContent", arg);

            assertEquals("Click Me + Submit", result);
            System.out.println("[Test] mixHandlesAndPrimitives: " + result);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 5. 环境隔离：测试变量不能直接在页面中使用
    // ─────────────────────────────────────────────────────────────

    @Test
    void environmentIsolation() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            String testVar = "hello from test";

            // ❌ 错误方式：页面中不存在 testVar（会抛异常）
            assertThrows(PlaywrightException.class, () -> {
                page.evaluate("() => testVar");
            });

            // ✅ 正确方式：作为参数传递
            String result = (String) page.evaluate("v => v", testVar);
            assertEquals("hello from test", result);
            System.out.println("[Test] environmentIsolation: must pass as argument, result=" + result);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 6. addInitScript：页面加载前注入 JS
    // ─────────────────────────────────────────────────────────────

    @Test
    void initScriptMocksMathRandom() {
        try (BrowserContext ctx = browser.newContext()) {
            // 在页面加载前注入：mock Math.random() 返回固定值
            ctx.addInitScript(
                "Math.random = () => 42;");

            Page page = ctx.newPage();
            loadPage(ctx, page);

            // Math.random 被 mock 了
            Object result = page.evaluate("() => Math.random()");
            assertEquals(42, ((Number) result).intValue());
            System.out.println("[Test] initScriptMocksMathRandom: Math.random() = " + result);
        }
    }

    @Test
    void initScriptSetsGlobalVar() {
        try (BrowserContext ctx = browser.newContext()) {
            // 在页面加载前设置全局变量
            ctx.addInitScript(
                "window.__TEST_DATA = { user: 'admin', token: 'abc123' };");

            Page page = ctx.newPage();
            loadPage(ctx, page);

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) page.evaluate(
                "() => window.__TEST_DATA");

            assertEquals("admin", result.get("user"));
            assertEquals("abc123", result.get("token"));
            System.out.println("[Test] initScriptSetsGlobalVar: " + result);
        }
    }

    @Test
    void pageLevelInitScript() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();

            // Page 级别 init script（只影响这个 page）
            page.addInitScript(
                "window.__PAGE_FLAG = 'injected';");

            loadPage(ctx, page);

            String result = (String) page.evaluate("() => window.__PAGE_FLAG");
            assertEquals("injected", result);
            System.out.println("[Test] pageLevelInitScript: " + result);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 7. evaluateHandle vs evaluate
    // ─────────────────────────────────────────────────────────────

    @Test
    void evaluateHandleReturnsHandle() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            // evaluate 返回序列化的值（拷贝）
            String text1 = (String) page.evaluate(
                "() => document.getElementById('btn1').textContent");

            // evaluateHandle 返回 JSHandle（引用，不拷贝）
            JSHandle handle = page.evaluateHandle(
                "() => document.getElementById('btn1')");
            String text2 = (String) handle.evaluate("el => el.textContent");

            assertEquals(text1, text2);

            // Handle 可以继续操作（如点击）——asElement() 转为 ElementHandle
            handle.asElement().click();
            Object count = page.evaluate("() => window.clickCount");
            assertEquals(1, ((Number) count).intValue());
            System.out.println("[Test] evaluateHandleReturnsHandle: text=" + text2
                    + ", clickCount=" + count);
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
