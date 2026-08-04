package com.research.playwright;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B22 - 官方文档 Extensibility 章节实践
 *
 * 本章核心：
 *   Selectors.register(name, engine) — 注册自定义选择器引擎
 *   - engine 需提供 query(root, selector) 和 queryAll(root, selector)
 *   - 注册后用 "name=selector" 语法在 locator 中使用
 *   - 必须在创建页面之前注册
 *
 * 运行方式：
 *   mvn test -Dtest=B22_Extensibility
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class B22_Extensibility {

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

        // ⚠️ 必须在创建 Page 之前注册选择器引擎
        // 注意：data-testid= 是 Playwright 内置引擎，无需注册
        registerTagNameEngine();
        registerTextContainsEngine();

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

    // ─────────────────────────────────────────────────────────────
    // 选择器引擎注册
    // ─────────────────────────────────────────────────────────────

    /**
     * tag= 引擎：直接用标签名查询（querySelector）
     * 用法：page.locator("tag=button")
     */
    private void registerTagNameEngine() {
        String engine = """
            {
              query(root, selector) {
                return root.querySelector(selector);
              },
              queryAll(root, selector) {
                return Array.from(root.querySelectorAll(selector));
              }
            }""";
        playwright.selectors().register("tag", engine);
        System.out.println("[Setup] Registered 'tag=' selector engine");
    }

    /**
     * data-testid= 是 Playwright 内置引擎，无需注册
     * 用法：page.locator("data-testid=submit-btn")
     * 等价于 page.locator("[data-testid=submit-btn]") 但更简洁
     */

    /**
     * text-contains= 引擎：通过文本内容模糊匹配
     * 用法：page.locator("text-contains=Submit")
     */
    private void registerTextContainsEngine() {
        String engine = """
            {
              hasDirectText(el, selector) {
                for (const node of el.childNodes) {
                  if (node.nodeType === 3 && node.textContent.includes(selector)) return true;
                }
                return false;
              },
              query(root, selector) {
                return Array.from(root.querySelectorAll('*')).find(el =>
                  this.hasDirectText(el, selector));
              },
              queryAll(root, selector) {
                return Array.from(root.querySelectorAll('*')).filter(el =>
                  this.hasDirectText(el, selector));
              }
            }""";
        playwright.selectors().register("text-contains", engine);
        System.out.println("[Setup] Registered 'text-contains=' selector engine");
    }

    // ─────────────────────────────────────────────────────────────
    // 测试页面
    // ─────────────────────────────────────────────────────────────

    private static final String PAGE_URL = "https://test.local";
    private static final String PAGE_HTML =
        "<html><head></head><body>" +
        "<div data-testid='container'>" +
        "  <button data-testid='submit-btn' id='btn1'>Submit</button>" +
        "  <button data-testid='cancel-btn' id='btn2'>Cancel</button>" +
        "  <a data-testid='home-link' href='/home'>Go Home</a>" +
        "</div>" +
        "<p data-testid='message'>Operation completed successfully</p>" +
        "<span>Plain text element</span>" +
        "</body></html>";

    private void loadPage(BrowserContext ctx, Page page) {
        ctx.route(PAGE_URL, r -> r.fulfill(
                new Route.FulfillOptions().setContentType("text/html").setBody(PAGE_HTML)));
        page.navigate(PAGE_URL);
    }

    // ─────────────────────────────────────────────────────────────
    // 1. tag= 引擎
    // ─────────────────────────────────────────────────────────────

    @Test
    void tagEngineBasicQuery() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            // tag=button 等价于 querySelector("button")
            String text = page.locator("tag=button").first().textContent();
            assertEquals("Submit", text);
            System.out.println("[Test] tagEngineBasicQuery: first button = " + text);
        }
    }

    @Test
    void tagEngineCountAll() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            int buttonCount = (int) page.locator("tag=button").count();
            assertEquals(2, buttonCount);
            System.out.println("[Test] tagEngineCountAll: " + buttonCount + " buttons");
        }
    }

    @Test
    void tagEngineCombinedWithBuiltIn() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            // tag=div 与 getByText 组合使用
            String text = page.locator("tag=div").getByText("Cancel").textContent();
            assertEquals("Cancel", text);

            // tag=a 与 first() 组合
            String linkText = page.locator("tag=a").first().textContent();
            assertEquals("Go Home", linkText);
            System.out.println("[Test] tagEngineCombinedWithBuiltIn: div+text=" + text
                    + ", a=" + linkText);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 2. data-testid= 引擎
    // ─────────────────────────────────────────────────────────────

    @Test
    void dataTestidEngineClick() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            // data-testid=submit-btn 比 [data-testid="submit-btn"] 更简洁
            String text = page.locator("data-testid=submit-btn").textContent();
            assertEquals("Submit", text);

            String cancel = page.locator("data-testid=cancel-btn").textContent();
            assertEquals("Cancel", cancel);
            System.out.println("[Test] dataTestidEngineClick: " + text + " / " + cancel);
        }
    }

    @Test
    void dataTestidEngineAll() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            // 查所有带 data-testid 的元素——用通配选择器
            int count = (int) page.locator("[data-testid]").count();
            assertEquals(5, count); // container, submit-btn, cancel-btn, home-link, message
            System.out.println("[Test] dataTestidEngineAll: " + count + " elements with data-testid");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 3. text-contains= 引擎
    // ─────────────────────────────────────────────────────────────

    @Test
    void textContainsEngineFind() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            // 模糊文本匹配
            String text = page.locator("text-contains=successfully").textContent();
            assertTrue(text.contains("Operation completed successfully"));
            System.out.println("[Test] textContainsEngineFind: " + text);
        }
    }

    @Test
    void textContainsEngineMultipleMatches() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            // 多个元素包含 "Submit" 文本
            // tag=button 的第一个是 "Submit"，text-contains=Submit 也应匹配
            int count = (int) page.locator("text-contains=Submit").count();
            assertTrue(count >= 1);
            System.out.println("[Test] textContainsEngineMultipleMatches: " + count + " elements contain 'Submit'");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 4. 三种引擎对比
    // ─────────────────────────────────────────────────────────────

    @Test
    void compareThreeEngines() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            // 同一个元素，三种方式定位
            String byTag = page.locator("tag=button").first().textContent();
            String byTestId = page.locator("data-testid=submit-btn").textContent();
            String byText = page.locator("text-contains=Submit").first().textContent();

            assertEquals("Submit", byTag);
            assertEquals("Submit", byTestId);
            assertEquals("Submit", byText);

            System.out.println("[Test] compareThreeEngines: tag=" + byTag
                    + ", data-testid=" + byTestId + ", text=" + byText);
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
