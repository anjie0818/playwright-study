package com.research.playwright;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * B33 - Page Object Models 章节测试
 * 
 * 测试内容：
 * 1. 基本 Page Object 模式
 * 2. 多个 Page Object 组合
 * 3. Page Object 封装复杂操作
 * 4. Page Object 最佳实践
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class B33_PageObjectModels {

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

    private void setupRoutes(BrowserContext ctx) {
        ctx.route("**/*", route -> {
            String url = route.request().url();
            
            if (url.contains("/home")) {
                route.fulfill(new Route.FulfillOptions()
                    .setContentType("text/html; charset=utf-8")
                    .setBody("<html><body>" +
                        "<h1>Home Page</h1>" +
                        "<nav>" +
                        "  <a href='/search'>Search</a>" +
                        "  <a href='/login'>Login</a>" +
                        "</nav>" +
                        "<div class='welcome'>Welcome to our site</div>" +
                        "</body></html>"));
            } else if (url.contains("/search")) {
                route.fulfill(new Route.FulfillOptions()
                    .setContentType("text/html; charset=utf-8")
                    .setBody("<html><body>" +
                        "<h1>Search Page</h1>" +
                        "<input type='text' aria-label='Enter your search term' id='search-input'/>" +
                        "<button id='search-btn'>Search</button>" +
                        "<div id='results'></div>" +
                        "<script>" +
                        "document.getElementById('search-btn').addEventListener('click', () => {" +
                        "  const term = document.getElementById('search-input').value;" +
                        "  document.getElementById('results').textContent = 'Results for: ' + term;" +
                        "});" +
                        "</script>" +
                        "</body></html>"));
            } else if (url.contains("/login")) {
                route.fulfill(new Route.FulfillOptions()
                    .setContentType("text/html; charset=utf-8")
                    .setBody("<html><body>" +
                        "<h1>Login Page</h1>" +
                        "<form id='login-form'>" +
                        "  <input type='text' id='username' placeholder='Username'/>" +
                        "  <input type='password' id='password' placeholder='Password'/>" +
                        "  <button type='submit'>Login</button>" +
                        "</form>" +
                        "<div id='login-result'></div>" +
                        "<script>" +
                        "document.getElementById('login-form').addEventListener('submit', (e) => {" +
                        "  e.preventDefault();" +
                        "  const user = document.getElementById('username').value;" +
                        "  document.getElementById('login-result').textContent = 'Logged in as: ' + user;" +
                        "});" +
                        "</script>" +
                        "</body></html>"));
            } else if (url.contains("/dashboard")) {
                route.fulfill(new Route.FulfillOptions()
                    .setContentType("text/html; charset=utf-8")
                    .setBody("<html><body>" +
                        "<h1>Dashboard</h1>" +
                        "<div class='stats'>" +
                        "  <div class='stat'>Users: 100</div>" +
                        "  <div class='stat'>Orders: 50</div>" +
                        "  <div class='stat'>Revenue: $1000</div>" +
                        "</div>" +
                        "</body></html>"));
            } else {
                route.fulfill(new Route.FulfillOptions()
                    .setContentType("text/html; charset=utf-8")
                    .setBody("<html><body><h1>Default Page</h1></body></html>"));
            }
        });
    }

    // ─────────────────────────────────────────────────────────────
    // Page Object 类定义
    // ─────────────────────────────────────────────────────────────

    /**
     * 搜索页面 Page Object
     */
    static class SearchPage {
        private final Page page;
        private final Locator searchTermInput;
        private final Locator searchButton;
        private final Locator results;

        public SearchPage(Page page) {
            this.page = page;
            this.searchTermInput = page.locator("[aria-label='Enter your search term']");
            this.searchButton = page.locator("#search-btn");
            this.results = page.locator("#results");
        }

        public void navigate() {
            page.navigate(BASE_URL + "/search");
        }

        public void search(String text) {
            searchTermInput.fill(text);
            searchButton.click();
        }

        public String getResultsText() {
            return results.textContent();
        }

        public Locator getResults() {
            return results;
        }
    }

    /**
     * 登录页面 Page Object
     */
    static class LoginPage {
        private final Page page;
        private final Locator usernameInput;
        private final Locator passwordInput;
        private final Locator loginButton;
        private final Locator loginResult;

        public LoginPage(Page page) {
            this.page = page;
            this.usernameInput = page.locator("#username");
            this.passwordInput = page.locator("#password");
            this.loginButton = page.locator("button[type='submit']");
            this.loginResult = page.locator("#login-result");
        }

        public void navigate() {
            page.navigate(BASE_URL + "/login");
        }

        public void login(String username, String password) {
            usernameInput.fill(username);
            passwordInput.fill(password);
            loginButton.click();
        }

        public String getLoginResult() {
            return loginResult.textContent();
        }

        public Locator getLoginResultLocator() {
            return loginResult;
        }
    }

    /**
     * 首页 Page Object
     */
    static class HomePage {
        private final Page page;
        private final Locator searchLink;
        private final Locator loginLink;
        private final Locator welcomeMessage;

        public HomePage(Page page) {
            this.page = page;
            this.searchLink = page.locator("a[href='/search']");
            this.loginLink = page.locator("a[href='/login']");
            this.welcomeMessage = page.locator(".welcome");
        }

        public void navigate() {
            page.navigate(BASE_URL + "/home");
        }

        public void goToSearch() {
            searchLink.click();
        }

        public void goToLogin() {
            loginLink.click();
        }

        public String getWelcomeText() {
            return welcomeMessage.textContent();
        }

        public Locator getWelcomeMessage() {
            return welcomeMessage;
        }
    }

    /**
     * Dashboard Page Object - 演示复杂操作封装
     */
    static class DashboardPage {
        private final Page page;
        private final Locator stats;

        public DashboardPage(Page page) {
            this.page = page;
            this.stats = page.locator(".stats");
        }

        public void navigate() {
            page.navigate(BASE_URL + "/dashboard");
        }

        public Map<String, String> getStats() {
            Map<String, String> statsMap = new HashMap<>();
            List<Locator> statElements = stats.locator(".stat").all();
            for (Locator stat : statElements) {
                String text = stat.textContent();
                String[] parts = text.split(": ");
                if (parts.length == 2) {
                    statsMap.put(parts[0].trim(), parts[1].trim());
                }
            }
            return statsMap;
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 测试用例
    // ─────────────────────────────────────────────────────────────

    @Test @Order(1)
    void basicPageObject() {
        try (BrowserContext ctx = browser.newContext()) {
            setupRoutes(ctx);
            Page page = ctx.newPage();

            SearchPage searchPage = new SearchPage(page);
            searchPage.navigate();
            searchPage.search("Playwright");

            assertThat(searchPage.getResults()).hasText("Results for: Playwright");
            System.out.println("[Test] basicPageObject: " + searchPage.getResultsText());
        }
    }

    @Test @Order(2)
    void multiplePageObjects() {
        try (BrowserContext ctx = browser.newContext()) {
            setupRoutes(ctx);
            Page page = ctx.newPage();

            // 使用首页 Page Object
            HomePage homePage = new HomePage(page);
            homePage.navigate();
            assertThat(homePage.getWelcomeMessage()).isVisible();
            assertEquals("Welcome to our site", homePage.getWelcomeText());

            // 导航到搜索页
            homePage.goToSearch();
            SearchPage searchPage = new SearchPage(page);
            searchPage.search("Test");
            assertThat(searchPage.getResults()).hasText("Results for: Test");

            System.out.println("[Test] multiplePageObjects: navigated home → search");
        }
    }

    @Test @Order(3)
    void pageObjectEncapsulation() {
        try (BrowserContext ctx = browser.newContext()) {
            setupRoutes(ctx);
            Page page = ctx.newPage();

            LoginPage loginPage = new LoginPage(page);
            loginPage.navigate();
            loginPage.login("admin", "secret123");

            assertThat(loginPage.getLoginResultLocator()).hasText("Logged in as: admin");
            System.out.println("[Test] pageObjectEncapsulation: " + loginPage.getLoginResult());
        }
    }

    @Test @Order(4)
    void pageObjectComplexOperations() {
        try (BrowserContext ctx = browser.newContext()) {
            setupRoutes(ctx);
            Page page = ctx.newPage();

            DashboardPage dashboard = new DashboardPage(page);
            dashboard.navigate();

            Map<String, String> stats = dashboard.getStats();
            assertEquals("100", stats.get("Users"));
            assertEquals("50", stats.get("Orders"));
            assertEquals("$1000", stats.get("Revenue"));

            System.out.println("[Test] pageObjectComplexOperations: " + stats);
        }
    }

    @Test @Order(5)
    void pageObjectFlowNavigation() {
        try (BrowserContext ctx = browser.newContext()) {
            setupRoutes(ctx);
            Page page = ctx.newPage();

            // 完整的用户流程
            HomePage homePage = new HomePage(page);
            homePage.navigate();

            // 从首页到登录
            homePage.goToLogin();
            LoginPage loginPage = new LoginPage(page);
            loginPage.login("user", "pass");
            assertThat(loginPage.getLoginResultLocator()).hasText("Logged in as: user");

            // 从登录到搜索
            homePage.navigate();
            homePage.goToSearch();
            SearchPage searchPage = new SearchPage(page);
            searchPage.search("Product");
            assertThat(searchPage.getResults()).hasText("Results for: Product");

            System.out.println("[Test] pageObjectFlowNavigation: home → login → search");
        }
    }

    @Test @Order(6)
    void pageObjectReuse() {
        try (BrowserContext ctx = browser.newContext()) {
            setupRoutes(ctx);
            Page page = ctx.newPage();

            SearchPage searchPage = new SearchPage(page);
            
            // 多次使用同一个 Page Object
            searchPage.navigate();
            searchPage.search("First");
            assertThat(searchPage.getResults()).hasText("Results for: First");

            searchPage.search("Second");
            assertThat(searchPage.getResults()).hasText("Results for: Second");

            searchPage.search("Third");
            assertThat(searchPage.getResults()).hasText("Results for: Third");

            System.out.println("[Test] pageObjectReuse: searched 3 times with same Page Object");
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
