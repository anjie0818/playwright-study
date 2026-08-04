package com.research.playwright;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * B31 - 官方文档 Other Locators 章节实践
 *
 * 本章核心（与 B26 Locators 互补，侧重高级和底层定位器）：
 *   1. CSS 选择器     — 基础 CSS + Playwright 增强伪类
 *   2. CSS 文本匹配   — :has-text() / :text() / :text-is() / :text-matches()
 *   3. CSS 可见性过滤 — :visible 伪类
 *   4. CSS :has()     — 包含特定子元素的父元素
 *   5. nth= 定位器    — 按索引选择第 n 个匹配元素
 *   6. 父元素定位     — filter(has) / xpath=..
 *   7. XPath 选择器   — xpath= 前缀或 // 开头
 *   8. 旧版文本定位器 — text= 语法（子串/精确/正则）
 *   9. ID/testId 简写 — id= / data-testid= / data-test-id= / data-test=
 *
 * 运行方式：
 *   mvn test -Dtest=B31_OtherLocators
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class B31_OtherLocators {

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

    private static final String BASE_URL = "https://test.local";

    /**
     * 综合测试页面 HTML
     */
    private static final String PAGE_HTML =
        "<html><head></head><body>" +
        // 导航栏
        "<nav id='nav-bar'>" +
        "  <a href='#'>Home</a>" +
        "  <a href='#'>Settings</a>" +
        "  <a href='#'>Log in</a>" +
        "</nav>" +
        // 文章列表
        "<article class='post'>" +
        "  <h2>Playwright Guide</h2>" +
        "  <p>Learn about Playwright testing.</p>" +
        "  <div class='promo'>Buy Now!</div>" +
        "</article>" +
        "<article class='post'>" +
        "  <h2>Selenium Tutorial</h2>" +
        "  <p>Another testing framework.</p>" +
        "</article>" +
        // 按钮组（包含不可见按钮）
        "<button style='display:none' class='btn'>Hidden</button>" +
        "<button class='btn'>Visible Button</button>" +
        "<button class='btn'>Another Button</button>" +
        // 表单
        "<form>" +
        "  <label for='username'>Username:</label>" +
        "  <input id='username' type='text' placeholder='Enter name'/>" +
        "  <label for='password'>Password:</label>" +
        "  <input id='password' type='password'/>" +
        "</form>" +
        // 多个 Buy 按钮（分散在不同层级）
        "<section><button class='buy'>Buy</button></section>" +
        "<article><div><button class='buy'>Buy</button></div></article>" +
        "<div><div><button class='buy'>Buy</button></div></div>" +
        // 列表
        "<ul>" +
        "  <li><label>Hello</label></li>" +
        "  <li><label>World</label></li>" +
        "</ul>" +
        // data 属性
        "<div id='main-content' data-testid='main' data-test-id='content' data-test='wrapper'>Content Area</div>" +
        // input type=button
        "<input type='button' value='Log in' id='input-btn'/>" +
        "</body></html>";

    private void loadPage(BrowserContext ctx, Page page) {
        ctx.route(BASE_URL + "/*", r -> r.fulfill(
                new Route.FulfillOptions().setContentType("text/html; charset=utf-8").setBody(PAGE_HTML)));
        page.navigate(BASE_URL + "/page");
    }

    // ─────────────────────────────────────────────────────────────
    // 1. CSS 选择器基础
    // ─────────────────────────────────────────────────────────────

    @Test @Order(1)
    void cssSelectorBasic() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            // 基本 CSS 选择器
            assertTrue(page.locator("css=button").count() > 0);
            assertTrue(page.locator("css=.btn").count() > 0);
            assertThat(page.locator("css=#nav-bar")).isVisible();

            // 组合选择器
            assertEquals(3, page.locator("css=nav a").count());

            System.out.println("[Test] cssSelectorBasic: nav links=" + page.locator("css=nav a").count());
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 2. CSS 文本匹配伪类
    // ─────────────────────────────────────────────────────────────

    @Test @Order(2)
    void cssHasText() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            // :has-text() 匹配包含文本的元素（子串匹配）
            // 必须与其他选择器组合使用，否则匹配 <body> 等
            assertThat(page.locator("article:has-text(\"Playwright\")")).isVisible();
            assertThat(page.locator("article:has-text(\"Selenium\")")).isVisible();

            int playwrightArticles = (int) page.locator("article:has-text(\"Playwright\")").count();
            assertEquals(1, playwrightArticles);

            System.out.println("[Test] cssHasText: Playwright articles=" + playwrightArticles);
        }
    }

    @Test @Order(3)
    void cssTextPseudo() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            // :text() — 匹配包含指定文本的最小元素
            assertThat(page.locator("#nav-bar :text('Home')")).isVisible();
            assertEquals("Home", page.locator("#nav-bar :text('Home')").textContent());

            // :text-is() — 精确匹配（区分大小写，完整匹配）
            assertThat(page.locator("#nav-bar :text-is('Home')")).isVisible();
            // "home"（小写）不应该匹配
            assertEquals(0, page.locator("#nav-bar :text-is('home')").count());

            // :text-matches() — 正则匹配
            assertThat(page.locator("#nav-bar :text-matches('Log\\\\s*in', 'i')")).isVisible();

            System.out.println("[Test] cssTextPseudo: :text(), :text-is(), :text-matches() all work");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 3. CSS 可见性过滤
    // ─────────────────────────────────────────────────────────────

    @Test @Order(4)
    void cssVisible() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            // css=button 匹配所有按钮（包括隐藏的）
            int allButtons = (int) page.locator("css=button").count();

            // css=button:visible 只匹配可见按钮
            int visibleButtons = (int) page.locator("css=button:visible").count();

            assertTrue(visibleButtons < allButtons,
                    "Visible buttons (" + visibleButtons + ") should be less than all (" + allButtons + ")");

            System.out.println("[Test] cssVisible: all=" + allButtons + ", visible=" + visibleButtons);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 4. CSS :has() — 包含特定子元素
    // ─────────────────────────────────────────────────────────────

    @Test @Order(5)
    void cssHas() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            // 找到包含 .promo 的 article
            int articlesWithPromo = (int) page.locator("article:has(div.promo)").count();
            assertTrue(articlesWithPromo >= 1, "Should have at least 1 article with promo");

            // 获取含 promo 的 article 的标题
            String promoTitle = page.locator("article:has(div.promo) h2").first().textContent();
            assertEquals("Playwright Guide", promoTitle);

            System.out.println("[Test] cssHas: with promo=" + articlesWithPromo + ", title=" + promoTitle);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 5. nth= 定位器 — 按索引选择
    // ─────────────────────────────────────────────────────────────

    @Test @Order(6)
    void nthLocator() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            // 第一个按钮（nth=0）
            String firstText = page.locator("button:visible").locator("nth=0").textContent();

            // 最后一个按钮（nth=-1）
            String lastText = page.locator("button:visible").locator("nth=-1").textContent();

            assertNotEquals(firstText, lastText);
            System.out.println("[Test] nthLocator: first=" + firstText + ", last=" + lastText);
        }
    }

    @Test @Order(7)
    void nthMatchSelectNthFromAll() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            // :nth-match() 选择全局第 n 个匹配（不要求兄弟）
            // 三个 Buy 按钮分散在不同层级
            assertThat(page.locator("button.buy")).hasCount(3);

            // 选择第三个 Buy 按钮
            String thirdText = page.locator("button.buy").locator("nth=2").textContent();
            assertEquals("Buy", thirdText);

            System.out.println("[Test] nthMatchSelectNthFromAll: 3 Buy buttons, selected 3rd=" + thirdText);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 6. 父元素定位
    // ─────────────────────────────────────────────────────────────

    @Test @Order(8)
    void parentElementLocator() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            // 方式 1：filter(has) — 通过子元素过滤父元素
            Locator helloLabel = page.getByText("Hello");
            Locator parentLi = page.getByRole(AriaRole.LISTITEM)
                    .filter(new Locator.FilterOptions().setHas(helloLabel));
            assertThat(parentLi).isVisible();
            assertTrue(parentLi.evaluate("el => el.tagName").toString().toLowerCase().contains("li"));

            // 方式 2：xpath=.. — 通过 XPath 找父元素（不推荐）
            Locator parentViaXpath = page.getByText("Hello").locator("xpath=..");
            assertThat(parentViaXpath).isVisible();

            System.out.println("[Test] parentElementLocator: filter(has) and xpath=.. both work");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 7. XPath 选择器
    // ─────────────────────────────────────────────────────────────

    @Test @Order(9)
    void xpathSelector() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            // xpath= 前缀
            assertTrue(page.locator("xpath=//button").count() > 0);
            int allButtons = (int) page.locator("xpath=//button").count();

            // // 开头自动识别为 XPath
            int allButtonsAuto = (int) page.locator("//button").count();
            assertEquals(allButtons, allButtonsAuto);

            // XPath 带条件
            assertEquals(2, page.locator("xpath=//article[@class='post']").count());

            // XPath 父元素 — 用 .first() 避免多匹配
            assertThat(page.locator("//label[text()='Username:']/..").first()).isVisible();

            System.out.println("[Test] xpathSelector: buttons=" + allButtons + ", articles=2");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 8. 旧版文本定位器 text=
    // ─────────────────────────────────────────────────────────────

    @Test @Order(10)
    void legacyTextLocator() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            // text= 子串匹配（不区分大小写）— 多个匹配用 first()
            assertThat(page.locator("text=Home").first()).isVisible();
            assertThat(page.locator("text=home").first()).isVisible(); // 不区分大小写

            // text="..." 精确匹配（区分大小写）
            assertThat(page.locator("text=\"Home\"").first()).isVisible();
            assertEquals(0, page.locator("text=\"home\"").count()); // 区分大小写

            // text=/regex/ 正则匹配
            assertThat(page.locator("text=/Log\\s*in/i").first()).isVisible();

            System.out.println("[Test] legacyTextLocator: substring, exact, regex all work");
        }
    }

    @Test @Order(11)
    void legacyTextInputButton() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            // input type=button 按 value 匹配，不按文本内容
            assertTrue(page.locator("text=Log in").count() > 0);
            // 同时匹配 <a>Log in</a> 和 <input value="Log in">
            int count = (int) page.locator("text=Log in").count();
            assertTrue(count >= 2, "Should match both <a> and <input>: " + count);

            System.out.println("[Test] legacyTextInputButton: matched " + count + " 'Log in' elements");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 9. ID / testId 简写选择器
    // ─────────────────────────────────────────────────────────────

    @Test @Order(12)
    void idAndTestIdSelectors() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            // id= 简写
            assertThat(page.locator("id=username")).isVisible();
            page.locator("id=username").fill("testuser");
            assertEquals("testuser", page.locator("id=username").inputValue());

            // data-testid= 简写
            assertThat(page.locator("data-testid=main")).isVisible();
            assertEquals("Content Area", page.locator("data-testid=main").textContent());

            // data-test-id= 简写
            assertThat(page.locator("data-test-id=content")).isVisible();

            // data-test= 简写
            assertThat(page.locator("data-test=wrapper")).isVisible();

            System.out.println("[Test] idAndTestIdSelectors: all shorthand selectors work");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 10. Label 到控件的重定向
    // ─────────────────────────────────────────────────────────────

    @Test @Order(13)
    void labelToControlRetargeting() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            // 通过 label 文本定位，fill 操作自动重定向到 input
            page.getByText("Username:").fill("admin");
            assertEquals("admin", page.locator("#username").inputValue());

            // 但 hasText 断言检查的是 label 本身 — 用 for 属性精确定位
            assertThat(page.locator("label[for='username']")).hasText("Username:");

            System.out.println("[Test] labelToControlRetargeting: fill via label → input");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 11. 定位器组合与对比
    // ─────────────────────────────────────────────────────────────

    @Test @Order(14)
    void locatorCombinationComparison() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            // 同一个元素，多种方式定位
            String textByRole = page.getByRole(AriaRole.LINK,
                    new Page.GetByRoleOptions().setName("Home")).textContent();
            String textByCSS = page.locator("#nav-bar :text-is('Home')").textContent();
            String textByLegacy = page.locator("text=\"Home\"").textContent();
            String textByXPath = page.locator("//a[text()='Home']").textContent();

            assertEquals(textByRole, textByCSS);
            assertEquals(textByCSS, textByLegacy);
            assertEquals(textByLegacy, textByXPath);

            System.out.println("[Test] locatorCombinationComparison: all methods → '" + textByRole + "'");
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
