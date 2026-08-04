package com.research.playwright;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * B12 - 官方文档 Assertions 章节实践
 *
 * 本章核心：
 *   Playwright 的 assertThat() 是"会等待"的断言（auto-retrying），与 JUnit 的
 *   assertTrue/assertEquals 等"立即判断"的断言截然不同。
 *
 *   覆盖知识点：
 *     1. Locator 断言：isVisible / isHidden / isEnabled / isDisabled / isChecked
 *     2. 文本断言：hasText / containsText（支持字符串和正则）
 *     3. 属性断言：hasAttribute / hasValue / hasCSS / hasId / hasClass
 *     4. 计数断言：hasCount
 *     5. Page 断言：hasTitle / hasURL
 *     6. API 断言：isOK（APIResponse）
 *     7. 否定断言：not.isVisible / not.hasText 等
 *     8. 软断言（用 JUnit assertAll 模拟）：收集所有失败后统一报告
 *     9. 自定义失败消息（1.56.0 版本说明与替代方案）
 *
 * 运行方式：
 *   mvn test -Dtest=B12_Assertions
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class B12_Assertions {

    private Playwright playwright;
    private Browser browser;

    @BeforeAll
    void beforeAll() {
        Map<String, String> env = new HashMap<>(System.getenv());
        env.put("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1");

        playwright = Playwright.create(new Playwright.CreateOptions().setEnv(env));

        String chromePath = resolveChromePath();
        BrowserType.LaunchOptions opts = new BrowserType.LaunchOptions()
                .setHeadless(true);
        if (chromePath != null) {
            opts.setExecutablePath(java.nio.file.Paths.get(chromePath));
        }
        browser = playwright.chromium().launch(opts);
        System.out.println("[Setup] Browser launched: " + (chromePath != null ? chromePath : "playwright-managed"));
    }

    @AfterAll
    void afterAll() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    // ─────────────────────────────────────────────────────────────
    // 1. Page 断言：hasTitle / hasURL
    // ─────────────────────────────────────────────────────────────

    /**
     * assertThat(page).hasTitle() / assertThat(page).hasURL()
     *
     * 这两个断言都会自动重试（等待），直到条件成立或超时。
     * 支持字符串精确匹配，也支持 Pattern 正则。
     */
    @Test
    void pageTitleAndUrl() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            page.navigate("https://playwright.dev/java/");

            assertThat(page).hasTitle(Pattern.compile("Playwright"));
            assertThat(page).hasURL(Pattern.compile("playwright\\.dev"));

            System.out.println("[Test] pageTitleAndUrl passed. title=" + page.title());
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 2. Locator 状态断言：isVisible / isHidden / isEnabled / isDisabled
    // ─────────────────────────────────────────────────────────────

    /**
     * 使用 setContent 注入 HTML，完全离线验证各类状态断言。
     *
     * isHidden 表示"元素不可见"，不等于"元素不存在"——
     * display:none / visibility:hidden / opacity:0 均属 hidden。
     */
    @Test
    void locatorStateAssertions() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            page.setContent(
                "<input id='enabled-input' value='hello' />" +
                "<input id='disabled-input' disabled value='world' />" +
                "<input type='checkbox' id='checked-box' checked />" +
                "<input type='checkbox' id='unchecked-box' />" +
                "<div id='visible-div'>I am visible</div>" +
                "<div id='hidden-div' style='display:none'>I am hidden</div>"
            );

            // isVisible / isHidden
            assertThat(page.locator("#visible-div")).isVisible();
            assertThat(page.locator("#hidden-div")).isHidden();

            // isEnabled / isDisabled
            assertThat(page.locator("#enabled-input")).isEnabled();
            assertThat(page.locator("#disabled-input")).isDisabled();

            // isChecked / not().isChecked
            assertThat(page.locator("#checked-box")).isChecked();
            assertThat(page.locator("#unchecked-box")).not().isChecked();

            System.out.println("[Test] locatorStateAssertions passed.");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 3. 文本断言：hasText / containsText（字符串 & 正则）
    // ─────────────────────────────────────────────────────────────

    /**
     * hasText      → 元素的 textContent 完整匹配（trimmed 后比较）
     * containsText → 元素 textContent 中包含该子串 / 匹配正则
     *
     * 对列表（多个元素）使用时，传入 String[] 或 Pattern[]，
     * hasText 会断言列表中每个元素与数组对应位置匹配。
     */
    @Test
    void textAssertions() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            page.setContent(
                "<h1>Hello Playwright!</h1>" +
                "<ul>" +
                "  <li>Apple</li>" +
                "  <li>Banana</li>" +
                "  <li>Cherry</li>" +
                "</ul>"
            );

            // 单元素：精确文本
            assertThat(page.locator("h1")).hasText("Hello Playwright!");

            // 单元素：包含子串
            assertThat(page.locator("h1")).containsText("Playwright");

            // 单元素：正则
            assertThat(page.locator("h1")).hasText(Pattern.compile("Hello .+!"));

            // 多元素列表：断言每个 <li> 的文本
            assertThat(page.locator("li")).hasText(new String[]{"Apple", "Banana", "Cherry"});

            // 否定：不包含某文本
            assertThat(page.locator("h1")).not().containsText("World");

            System.out.println("[Test] textAssertions passed.");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 4. 属性断言：hasAttribute / hasValue / hasCSS / hasId / hasClass
    // ─────────────────────────────────────────────────────────────

    /**
     * hasAttribute → 检查 DOM 属性（href、placeholder、data-*…）
     * hasValue     → 检查 input / select / textarea 的 value
     * hasCSS       → 检查计算后 CSS 属性（resolved style）
     * hasId        → 检查 id 属性
     * hasClass     → 检查 class 属性（完整类名字符串）
     */
    @Test
    void attributeAssertions() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            page.setContent(
                "<a id='link' href='https://example.com' class='nav-link active'>Example</a>" +
                "<input id='search' type='text' placeholder='Search...' value='playwright' />" +
                "<p id='para' style='color: rgb(255, 0, 0); font-size: 16px;'>Red text</p>"
            );

            // hasAttribute
            assertThat(page.locator("#link")).hasAttribute("href", "https://example.com");

            // hasId
            assertThat(page.locator("a")).hasId("link");

            // hasClass（完整 class 字符串）
            assertThat(page.locator("#link")).hasClass("nav-link active");

            // hasValue（input 当前值）
            assertThat(page.locator("#search")).hasValue("playwright");

            // hasAttribute 也可用正则
            assertThat(page.locator("#search")).hasAttribute("placeholder", Pattern.compile("Search.*"));

            // hasCSS（计算样式）
            assertThat(page.locator("#para")).hasCSS("color", "rgb(255, 0, 0)");

            System.out.println("[Test] attributeAssertions passed.");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 5. 计数断言：hasCount
    // ─────────────────────────────────────────────────────────────

    /**
     * hasCount 断言 Locator 匹配到的元素数量，常用于列表渲染验证。
     * 同样是 auto-retrying：适合等待异步渲染完成后再断言数量。
     */
    @Test
    void countAssertion() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            page.setContent(
                "<ul>" +
                "  <li class='item'>One</li>" +
                "  <li class='item'>Two</li>" +
                "  <li class='item'>Three</li>" +
                "</ul>"
            );

            assertThat(page.locator(".item")).hasCount(3);
            assertThat(page.locator("li")).hasCount(3);

            System.out.println("[Test] countAssertion passed.");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 6. 否定断言：not()
    // ─────────────────────────────────────────────────────────────

    /**
     * 所有 assertThat 断言都可以在前面加 .not() 取反。
     * .not() 同样具备 auto-retrying 语义——它会等待"条件变为不满足"，
     * 而不是立即检查并报失败，这点与普通断言 ! 取反截然不同。
     */
    @Test
    void negatingAssertions() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            page.setContent(
                "<div id='box' style='display:block'>Visible</div>" +
                "<span id='label'>Hello</span>"
            );

            assertThat(page.locator("#box")).not().isHidden();
            assertThat(page.locator("#label")).not().hasText("World");
            assertThat(page.locator("#label")).not().hasAttribute("class", "missing");

            System.out.println("[Test] negatingAssertions passed.");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 7. 自定义失败消息（版本说明）
    // ─────────────────────────────────────────────────────────────

    /**
     * 官方文档提到 withMessage() 可为断言附加自定义说明，失败时打印便于定位。
     *
     * Playwright Java 1.56.0 中 LocatorAssertions 接口尚未暴露 withMessage()。
     * 高版本预计支持：assertThat(locator).withMessage("Submit 按钮应可见").isVisible()
     *
     * 当前版本替代方案：在断言前用 System.out.println 打印诊断上下文，
     * 或在外层 catch 中重新包装异常消息。
     */
    @Test
    void customFailureMessage() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            page.setContent("<button id='submit'>Submit</button>");

            // 1.56.0 替代做法：断言前打印上下文信息
            System.out.println("[Assert] Submit button should be visible on page load");
            assertThat(page.locator("#submit")).isVisible();

            System.out.println("[Assert] Submit button text should be 'Submit'");
            assertThat(page.locator("#submit")).hasText("Submit");

            System.out.println("[Test] customFailureMessage passed.");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 8. 软断言（SoftAssertions）
    // ─────────────────────────────────────────────────────────────

    /**
     * 官方概念：普通 assertThat 失败后会立即停止（fail-fast）。
     * SoftAssertions 收集所有失败后统一报告，适合一次性验证多个独立元素。
     *
     * Playwright Java 1.56.0 尚未暴露原生 SoftAssertions 接口。
     * 高版本原生写法参考：
     *   var softly = new com.microsoft.playwright.assertions.PlaywrightAssertions.SoftAssertions();
     *   softly.assertThat(locator).isVisible();
     *   softly.assertAll();
     *
     * 当前版本替代方案：JUnit 5 的 assertAll()，语义完全等价——
     * 所有 lambda 都会执行，最后汇总报告所有失败。
     */
    @Test
    void softAssertions() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            page.setContent(
                "<h1>Dashboard</h1>" +
                "<nav id='menu'>Menu</nav>" +
                "<footer id='footer'>Footer</footer>"
            );

            // 用 JUnit 5 assertAll 实现软断言：收集所有失败后统一报告
            org.junit.jupiter.api.Assertions.assertAll(
                () -> assertThat(page.locator("h1")).hasText("Dashboard"),
                () -> assertThat(page.locator("#menu")).isVisible(),
                () -> assertThat(page.locator("#footer")).isVisible(),
                () -> assertThat(page.locator("h1")).not().hasText("Login")
            );

            System.out.println("[Test] softAssertions (via JUnit assertAll) passed.");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 9. API Response 断言：isOK()
    // ─────────────────────────────────────────────────────────────

    /**
     * assertThat(response).isOK() 断言 HTTP 响应状态码在 200-299 范围内。
     * 这是 API testing 场景下最常用的 Playwright 原生断言。
     *
     * 注意：APIRequestContext 不实现 AutoCloseable，需手动 dispose()。
     */
    @Test
    void apiResponseAssertion() {
        APIRequestContext request = playwright.request().newContext(
                new APIRequest.NewContextOptions().setBaseURL("https://jsonplaceholder.typicode.com")
        );
        try {
            APIResponse response = request.get("/posts/1");

            // Playwright 原生断言（非 JUnit assertTrue）
            assertThat(response).isOK();

            System.out.println("[Test] apiResponseAssertion passed. status=" + response.status());
        } finally {
            request.dispose();
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 工具方法：探测系统 Chrome 路径
    // ─────────────────────────────────────────────────────────────

    private static String resolveChromePath() {
        String[] candidates = {
            "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
            "/usr/bin/google-chrome",
            "/usr/bin/chromium-browser",
            "/usr/bin/chromium"
        };
        for (String path : candidates) {
            if (new java.io.File(path).exists()) return path;
        }
        return null;
    }
}
