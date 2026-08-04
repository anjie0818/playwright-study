package com.research.playwright;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * B26 - 官方文档 Locators 章节实践
 *
 * 本章核心（7 种内置定位器 + CSS/XPath）：
 *   1. getByRole()       — 按无障碍角色定位（★ 最推荐）
 *   2. getByLabel()      — 按关联 label 文本定位表单控件
 *   3. getByPlaceholder() — 按 placeholder 属性定位
 *   4. getByText()       — 按文本内容定位（子串/精确/正则）
 *   5. getByAltText()    — 按 alt 属性定位图片
 *   6. getByTitle()      — 按 title 属性定位
 *   7. getByTestId()     — 按 data-testid 属性定位
 *   8. locator(css/xpath) — CSS 或 XPath 选择器
 *
 *   优先级建议：getByRole > getByLabel/getByPlaceholder > getByText > getByTestId > CSS/XPath
 *
 * 运行方式：
 *   mvn test -Dtest=B26_Locators
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class B26_Locators {

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

    /**
     * 综合测试页面：包含所有定位器可定位的元素
     */
    private static final String PAGE_HTML =
        "<html><head></head><body>" +
        "<h1>Sign up</h1>" +
        "<h3>Account Details</h3>" +
        "<form>" +
        "  <label>User Name <input type='text' id='username' placeholder='Enter your name'/></label>" +
        "  <label>Password <input type='password' id='password' placeholder='Enter password'/></label>" +
        "  <input type='email' id='email' placeholder='name@example.com' aria-label='Email Address'/>" +
        "  <label>" +
        "    <input type='checkbox' id='subscribe'/> Subscribe to newsletter" +
        "  </label>" +
        "  <button type='submit' id='submit-btn'>Sign in</button>" +
        "  <button type='reset'>Reset</button>" +
        "</form>" +
        "<img alt='Playwright Logo' src='/logo.png' width='100'/>" +
        "<span title='Issues count'>25 issues</span>" +
        "<div data-testid='directions' id='directions'>Itinéraire</div>" +
        "<div data-pw='custom-pw-element' id='pw-elem'>Custom PW</div>" +
        "<ul>" +
        "  <li data-testid='item-1'>Apple</li>" +
        "  <li data-testid='item-2'>Banana</li>" +
        "  <li data-testid='item-3'>Cherry</li>" +
        "</ul>" +
        "<p class='info-text'>Welcome, John</p>" +
        "<a href='#' class='nav-link'>Home</a>" +
        "<a href='#' class='nav-link'>Settings</a>" +
        "<div id='result'>none</div>" +
        "<script>" +
        "document.getElementById('submit-btn').addEventListener('click', e => {" +
        "  e.preventDefault();" +
        "  document.getElementById('result').textContent = 'submitted';" +
        "});" +
        "</script>" +
        "</body></html>";

    private void loadPage(BrowserContext ctx, Page page) {
        ctx.route(PAGE_URL, r -> r.fulfill(
                new Route.FulfillOptions().setContentType("text/html; charset=utf-8").setBody(PAGE_HTML)));
        page.navigate(PAGE_URL);
    }

    // ─────────────────────────────────────────────────────────────
    // 1. getByRole — 按无障碍角色定位（★ 最推荐）
    // ─────────────────────────────────────────────────────────────

    @Test
    void getByRoleHeading() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            // h1 → heading role
            assertThat(page.getByRole(AriaRole.HEADING,
                    new Page.GetByRoleOptions().setName("Sign up"))).isVisible();

            // h3 → heading role
            assertThat(page.getByRole(AriaRole.HEADING,
                    new Page.GetByRoleOptions().setName("Account Details"))).isVisible();

            System.out.println("[Test] getByRoleHeading: h1 and h3 found");
        }
    }

    @Test
    void getByRoleButton() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            // button role + name
            page.getByRole(AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName("Sign in")).click();

            String result = page.locator("#result").textContent();
            assertEquals("submitted", result);
            System.out.println("[Test] getByRoleButton: clicked, result=" + result);
        }
    }

    @Test
    void getByRoleCheckbox() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            page.getByRole(AriaRole.CHECKBOX,
                    new Page.GetByRoleOptions().setName("Subscribe to newsletter")).check();

            assertThat(page.getByRole(AriaRole.CHECKBOX,
                    new Page.GetByRoleOptions().setName("Subscribe to newsletter"))).isChecked();

            System.out.println("[Test] getByRoleCheckbox: checked");
        }
    }

    @Test
    void getByRoleLink() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            assertThat(page.getByRole(AriaRole.LINK,
                    new Page.GetByRoleOptions().setName("Home"))).isVisible();
            assertThat(page.getByRole(AriaRole.LINK,
                    new Page.GetByRoleOptions().setName("Settings"))).isVisible();

            System.out.println("[Test] getByRoleLink: Home and Settings links found");
        }
    }

    @Test
    void getByRoleWithRegex() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            // 正则匹配按钮名（忽略大小写）
            page.getByRole(AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName(
                            Pattern.compile("sign in", Pattern.CASE_INSENSITIVE))).click();

            assertEquals("submitted", page.locator("#result").textContent());
            System.out.println("[Test] getByRoleWithRegex: case-insensitive match");
        }
    }

    @Test
    void getByRoleCount() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            // 统计所有 button
            int buttonCount = (int) page.getByRole(AriaRole.BUTTON).count();
            assertEquals(2, buttonCount); // Sign in + Reset

            // 统计所有 listitem
            int itemCount = (int) page.getByRole(AriaRole.LISTITEM).count();
            assertEquals(3, itemCount); // Apple, Banana, Cherry

            System.out.println("[Test] getByRoleCount: buttons=" + buttonCount + ", listitems=" + itemCount);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 2. getByLabel — 按 label 文本定位表单控件
    // ─────────────────────────────────────────────────────────────

    @Test
    void getByLabelFill() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            page.getByLabel("User Name").fill("Alice");
            page.getByLabel("Password").fill("secret123");

            String username = page.locator("#username").inputValue();
            String password = page.locator("#password").inputValue();

            assertEquals("Alice", username);
            assertEquals("secret123", password);
            System.out.println("[Test] getByLabelFill: username=" + username + ", password=***");
        }
    }

    @Test
    void getByLabelAriaLabel() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            // aria-label 也作为 label
            page.getByLabel("Email Address").fill("test@example.com");

            String email = page.locator("#email").inputValue();
            assertEquals("test@example.com", email);
            System.out.println("[Test] getByLabelAriaLabel: email=" + email);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 3. getByPlaceholder — 按 placeholder 属性定位
    // ─────────────────────────────────────────────────────────────

    @Test
    void getByPlaceholder() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            page.getByPlaceholder("Enter your name").fill("Bob");
            page.getByPlaceholder("Enter password").fill("pass");
            page.getByPlaceholder("name@example.com").fill("bob@test.com");

            assertEquals("Bob", page.locator("#username").inputValue());
            assertEquals("bob@test.com", page.locator("#email").inputValue());
            System.out.println("[Test] getByPlaceholder: all inputs filled");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 4. getByText — 按文本内容定位
    // ─────────────────────────────────────────────────────────────

    @Test
    void getByTextSubstring() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            // 子串匹配
            assertThat(page.getByText("Welcome, John")).isVisible();
            assertThat(page.getByText("Welcome")).isVisible();

            System.out.println("[Test] getByTextSubstring: 'Welcome, John' and 'Welcome' both found");
        }
    }

    @Test
    void getByTextExact() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            // 精确匹配
            assertThat(page.getByText("Welcome, John",
                    new Page.GetByTextOptions().setExact(true))).isVisible();

            // "Welcome" 精确匹配不应该找到 "Welcome, John"
            int count = (int) page.getByText("Welcome",
                    new Page.GetByTextOptions().setExact(true)).count();
            assertEquals(0, count, "Exact 'Welcome' should not match 'Welcome, John'");

            System.out.println("[Test] getByTextExact: exact match works, substring rejected");
        }
    }

    @Test
    void getByTextRegex() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            // 正则匹配（忽略大小写 + 尾部匹配）
            assertThat(page.getByText(Pattern.compile("welcome, john$", Pattern.CASE_INSENSITIVE)))
                    .isVisible();

            System.out.println("[Test] getByTextRegex: case-insensitive regex match");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 5. getByAltText — 按 alt 属性定位
    // ─────────────────────────────────────────────────────────────

    @Test
    void getByAltText() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            assertThat(page.getByAltText("Playwright Logo")).isVisible();
            String src = page.getByAltText("Playwright Logo").getAttribute("src");
            assertEquals("/logo.png", src);
            System.out.println("[Test] getByAltText: logo found, src=" + src);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 6. getByTitle — 按 title 属性定位
    // ─────────────────────────────────────────────────────────────

    @Test
    void getByTitle() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            assertThat(page.getByTitle("Issues count")).hasText("25 issues");
            System.out.println("[Test] getByTitle: 'Issues count' = '25 issues'");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 7. getByTestId — 按 data-testid 属性定位
    // ─────────────────────────────────────────────────────────────

    @Test
    void getByTestId() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            assertThat(page.getByTestId("directions")).hasText("Itinéraire");
            assertThat(page.getByTestId("item-2")).hasText("Banana");

            System.out.println("[Test] getByTestId: directions and item-2 found");
        }
    }

    @Test
    void getByTestIdMultiple() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            // 获取所有带 data-testid 的列表项
            int count = (int) page.locator("[data-testid^='item-']").count();
            assertEquals(3, count);
            System.out.println("[Test] getByTestIdMultiple: " + count + " items");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 8. 自定义 testId 属性
    // ─────────────────────────────────────────────────────────────

    @Test
    void customTestIdAttribute() {
        // 自定义 testId 属性必须在 Playwright 实例级别设置（影响所有 Context/Page）
        playwright.selectors().setTestIdAttribute("data-pw");

        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            // 现在 getByTestId 查找 data-pw 而非 data-testid
            assertThat(page.getByTestId("custom-pw-element")).hasText("Custom PW");
            System.out.println("[Test] customTestIdAttribute: data-pw='custom-pw-element' found");
        } finally {
            // 恢复默认
            playwright.selectors().setTestIdAttribute("data-testid");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 9. CSS / XPath 选择器
    // ─────────────────────────────────────────────────────────────

    @Test
    void cssSelector() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            // CSS 选择器
            assertThat(page.locator("button[type='submit']")).hasText("Sign in");
            assertThat(page.locator(".info-text")).hasText("Welcome, John");
            assertThat(page.locator("#result")).hasText("none");

            // 多个匹配
            int linkCount = (int) page.locator("a.nav-link").count();
            assertEquals(2, linkCount);

            System.out.println("[Test] cssSelector: button, .info-text, #result, nav-links=" + linkCount);
        }
    }

    @Test
    void xpathSelector() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            // XPath 选择器
            assertThat(page.locator("xpath=//h1")).hasText("Sign up");
            assertThat(page.locator("xpath=//ul/li[2]")).hasText("Banana");

            System.out.println("[Test] xpathSelector: h1 and 2nd li found");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 10. 链式定位：缩小范围
    // ─────────────────────────────────────────────────────────────

    @Test
    void chainedLocators() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            // 先定位 ul，再在其中定位第二个 li
            String text = page.locator("ul")
                    .getByRole(AriaRole.LISTITEM).nth(1).textContent();
            assertEquals("Banana", text);

            // 先定位 form，再定位其中的 submit 按钮
            assertThat(page.locator("form")
                    .getByRole(AriaRole.BUTTON,
                            new Locator.GetByRoleOptions().setName("Sign in"))).isVisible();

            System.out.println("[Test] chainedLocators: ul>li[1]=" + text);
        }
    }

    @Test
    void filterByText() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            // 在所有 listitem 中过滤文本包含 "Cherry" 的
            String text = page.getByRole(AriaRole.LISTITEM)
                    .filter(new Locator.FilterOptions().setHasText("Cherry"))
                    .textContent();
            assertEquals("Cherry", text);
            System.out.println("[Test] filterByText: found=" + text);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 11. Locator 每次重新查找
    // ─────────────────────────────────────────────────────────────

    @Test
    void locatorRefetches() {
        try (BrowserContext ctx = browser.newContext()) {
            Page page = ctx.newPage();
            loadPage(ctx, page);

            Locator resultLocator = page.locator("#result");

            // 第一次：none
            assertEquals("none", resultLocator.textContent());

            // 点击按钮修改 #result
            page.getByRole(AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName("Sign in")).click();

            // 第二次：同一个 locator，重新查找得到最新值
            assertEquals("submitted", resultLocator.textContent());

            System.out.println("[Test] locatorRefetches: none → submitted");
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
