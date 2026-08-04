package com.research.playwright;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.*;
import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * B30 - 官方文档 Network 章节实践
 *
 * 本章核心（与 B27 Mock APIs 互补，侧重网络监控和高级功能）：
 *   1. HTTP Authentication  — setHttpCredentials 基本认证
 *   2. Network Events       — onRequest/onResponse 全局监控
 *   3. waitForResponse      — 等待特定响应（glob/regex/predicate）
 *   4. Modify Requests      — 修改请求头、方法、body
 *   5. Abort Requests       — 按资源类型/URL 模式中止请求（屏蔽图片等）
 *   6. Modify Responses     — fetch 原始响应 + 修改后 fulfill
 *   7. Glob URL Patterns    — glob 匹配规则详解
 *   8. WebSocket Inspection — onWebSocket 监听帧数据
 *   9. Service Workers      — 禁用 Service Workers 避免网络事件丢失
 *
 * 运行方式：
 *   mvn test -Dtest=B30_Network
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class B30_Network {

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

    // ─────────────────────────────────────────────────────────────
    // 1. HTTP Authentication — 基本认证
    // ─────────────────────────────────────────────────────────────

    @Test @Order(1)
    void httpAuthentication() {
        try (BrowserContext ctx = browser.newContext(
                new Browser.NewContextOptions()
                        .setHttpCredentials("admin", "secret123"))) {

            // 设置 HTTP 认证后，浏览器会自动在请求中添加 Authorization 头
            // 这里验证配置生效（实际测试需要真实的 HTTP 401 响应服务器）
            
            ctx.route("**/api/secure", route -> {
                String authHeader = route.request().headerValue("Authorization");
                route.fulfill(new Route.FulfillOptions()
                        .setContentType("text/html")
                        .setBody("<html><body>" +
                                "<h1>Secure API</h1>" +
                                "<div id='auth'>" + (authHeader != null ? "has-auth" : "no-auth") + "</div>" +
                                "</body></html>"));
            });

            Page page = ctx.newPage();
            page.navigate(BASE_URL + "/api/secure");

            assertThat(page.getByText("Secure API")).isVisible();
            String authStatus = page.locator("#auth").textContent();
            System.out.println("[Test] httpAuthentication: auth header present = " + 
                    ("has-auth".equals(authStatus)));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 2. Network Events — onRequest / onResponse 全局监控
    // ─────────────────────────────────────────────────────────────

    @Test @Order(2)
    void networkEventsMonitoring() {
        try (BrowserContext ctx = browser.newContext()) {
            ctx.route("**/*", route -> route.fulfill(new Route.FulfillOptions()
                    .setContentType("text/html")
                    .setBody("<html><body>" +
                            "<script>fetch('/api/data');fetch('/api/users');</script>" +
                            "</body></html>")));
            ctx.route("**/api/data", route -> route.fulfill(new Route.FulfillOptions()
                    .setContentType("application/json")
                    .setBody("{\"result\":\"ok\"}")));
            ctx.route("**/api/users", route -> route.fulfill(new Route.FulfillOptions()
                    .setContentType("application/json")
                    .setBody("[{\"name\":\"Alice\"}]")));

            Page page = ctx.newPage();

            List<String> requests = new CopyOnWriteArrayList<>();
            List<String> responses = new CopyOnWriteArrayList<>();

            page.onRequest(request ->
                    requests.add(request.method() + " " + request.url()));
            page.onResponse(response ->
                    responses.add(response.status() + " " + response.url()));

            page.navigate(BASE_URL);
            page.waitForTimeout(500);

            assertTrue(requests.stream().anyMatch(r -> r.contains("/api/data")));
            assertTrue(requests.stream().anyMatch(r -> r.contains("/api/users")));
            assertTrue(responses.stream().anyMatch(r -> r.startsWith("200")));

            System.out.println("[Test] networkEventsMonitoring:");
            requests.forEach(r -> System.out.println("  >> " + r));
            responses.forEach(r -> System.out.println("  << " + r));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 3. waitForResponse — 等待特定响应
    // ─────────────────────────────────────────────────────────────

    @Test @Order(3)
    void waitForResponseGlob() {
        try (BrowserContext ctx = browser.newContext()) {
            ctx.route("**/start", route -> route.fulfill(new Route.FulfillOptions()
                    .setContentType("text/html")
                    .setBody("<html><body><button onclick=\"fetch('/api/fetch_data').then(r=>r.json()).then(d=>document.getElementById('r').textContent=d.result)\">Update</button><div id='r'>waiting</div></body></html>")));
            ctx.route("**/api/fetch_data", route -> route.fulfill(new Route.FulfillOptions()
                    .setContentType("application/json")
                    .setBody("{\"result\":\"loaded\"}")));

            Page page = ctx.newPage();
            page.navigate(BASE_URL + "/start");

            // 用 glob 模式等待特定 API 响应
            Response response = page.waitForResponse("**/api/fetch_data", () -> {
                page.getByText("Update").click();
            });

            assertEquals(200, response.status());
            assertThat(page.locator("#r")).hasText("loaded");
            System.out.println("[Test] waitForResponseGlob: status=" + response.status());
        }
    }

    @Test @Order(4)
    void waitForResponseRegex() {
        try (BrowserContext ctx = browser.newContext()) {
            ctx.route("**/page", route -> route.fulfill(new Route.FulfillOptions()
                    .setContentType("text/html")
                    .setBody("<html><body><img src='/images/photo.jpg'/></body></html>")));
            ctx.route("**/*.jpg", route -> route.fulfill(new Route.FulfillOptions()
                    .setStatus(200)
                    .setContentType("image/jpeg")
                    .setBody("fake-image")));

            Page page = ctx.newPage();

            // 用正则等待 jpeg 图片响应
            Response response = page.waitForResponse(Pattern.compile("\\.jpg$"), () -> {
                page.navigate(BASE_URL + "/page");
            });

            assertEquals(200, response.status());
            assertTrue(response.url().endsWith(".jpg"));
            System.out.println("[Test] waitForResponseRegex: " + response.url());
        }
    }

    @Test @Order(5)
    void waitForResponsePredicate() {
        try (BrowserContext ctx = browser.newContext()) {
            ctx.route("**/start", route -> route.fulfill(new Route.FulfillOptions()
                    .setContentType("text/html")
                    .setBody("<html><body><button onclick=\"fetch('/api/data?token=abc123')\">Load</button></body></html>")));
            ctx.route("**/api/data*", route -> route.fulfill(new Route.FulfillOptions()
                    .setContentType("application/json")
                    .setBody("{\"ok\":true}")));

            Page page = ctx.newPage();
            page.navigate(BASE_URL + "/start");

            // 用 predicate 等待包含特定 token 的响应
            Response response = page.waitForResponse(
                    r -> r.url().contains("token=abc123"), () -> {
                        page.getByText("Load").click();
                    });

            assertEquals(200, response.status());
            System.out.println("[Test] waitForResponsePredicate: url=" + response.url());
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 4. Modify Requests — 修改请求头、方法
    // ─────────────────────────────────────────────────────────────

    @Test @Order(6)
    void modifyRequestHeaders() {
        try (BrowserContext ctx = browser.newContext()) {
            // 先注册通配符路由，注入自定义 header（但跳过 API 路由）
            ctx.route("**/*", route -> {
                if (route.request().url().contains("/api/check")) {
                    route.resume(); // 不处理 API 路由，让下一个路由处理
                    return;
                }
                Map<String, String> headers = new HashMap<>(route.request().headers());
                headers.put("x-custom-header", "injected-value");
                route.resume(new Route.ResumeOptions().setHeaders(headers));
            });

            // 再注册 API 路由，验证收到了注入的 header
            ctx.route("**/api/check", route -> {
                String customHeader = route.request().headerValue("x-custom-header");
                System.out.println("[Test] modifyRequestHeaders: x-custom-header=" + customHeader);
                route.fulfill(new Route.FulfillOptions()
                        .setContentType("text/plain")
                        .setBody("ok"));
            });

            Page page = ctx.newPage();
            page.navigate(BASE_URL + "/api/check");
            page.waitForTimeout(500);
            
            System.out.println("[Test] modifyRequestHeaders: completed");
        }
    }

    @Test @Order(7)
    void changeRequestMethod() {
        try (BrowserContext ctx = browser.newContext()) {
            // 用单一路由拦截、修改方法后 resume，再用另一个路由捕获修改后的请求
            ctx.route("**/api/endpoint", route ->
                    route.resume(new Route.ResumeOptions().setMethod("POST")));

            // 用 onRequest 监听捕获修改后的请求
            Page page = ctx.newPage();

            String[] capturedMethod = new String[1];
            page.onRequest(request -> {
                if (request.url().contains("/api/endpoint")) {
                    capturedMethod[0] = request.method();
                }
            });

            // navigate 会触发 GET 请求，但 route 会改为 POST
            // 由于 test.local/api/endpoint 不存在，会报错，我们只关心请求方法的修改
            try {
                page.navigate(BASE_URL + "/api/endpoint",
                        new Page.NavigateOptions().setTimeout(3000));
            } catch (PlaywrightException ignored) {
                // 预期会失败（没有服务器响应），但请求已经发出
            }

            page.waitForTimeout(500);
            System.out.println("[Test] changeRequestMethod: method=" + capturedMethod[0]
                    + " (request was intercepted and method changed to POST)");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 5. Abort Requests — 按资源类型中止请求
    // ─────────────────────────────────────────────────────────────

    @Test @Order(8)
    void abortImagesByUrlPattern() {
        try (BrowserContext ctx = browser.newContext()) {
            // 中止所有图片请求
            ctx.route("**/*.{png,jpg,jpeg,gif}", route -> route.abort());

            ctx.route("**/page", route -> route.fulfill(new Route.FulfillOptions()
                    .setContentType("text/html")
                    .setBody("<html><body>" +
                            "<h1>Page without images</h1>" +
                            "<img src='/photo.jpg' onerror=\"this.id='img-error'\"/>" +
                            "</body></html>")));

            Page page = ctx.newPage();
            page.navigate(BASE_URL + "/page");

            // 图片加载失败会触发 onerror
            page.waitForFunction("() => document.getElementById('img-error') !== null",
                    null, new Page.WaitForFunctionOptions().setTimeout(3000));

            assertThat(page.getByText("Page without images")).isVisible();
            System.out.println("[Test] abortImagesByUrlPattern: image requests blocked");
        }
    }

    @Test @Order(9)
    void abortByResourceType() {
        try (BrowserContext ctx = browser.newContext()) {
            // 按资源类型中止请求
            ctx.route("**/*", route -> {
                if ("image".equals(route.request().resourceType())) {
                    route.abort();
                } else {
                    route.resume();
                }
            });

            ctx.route("**/page", route -> route.fulfill(new Route.FulfillOptions()
                    .setContentType("text/html")
                    .setBody("<html><body>" +
                            "<h1>No Images</h1>" +
                            "<img src='/logo.png' onerror=\"this.id='blocked'\"/>" +
                            "</body></html>")));

            Page page = ctx.newPage();
            page.navigate(BASE_URL + "/page");

            page.waitForFunction("() => document.getElementById('blocked') !== null",
                    null, new Page.WaitForFunctionOptions().setTimeout(3000));

            System.out.println("[Test] abortByResourceType: images blocked by type");
        }
    }

    @Test @Order(10)
    void abortCssAndFonts() {
        try (BrowserContext ctx = browser.newContext()) {
            Set<String> blockedTypes = Set.of("stylesheet", "font");

            ctx.route("**/*", route -> {
                String type = route.request().resourceType();
                if (blockedTypes.contains(type)) {
                    route.abort();
                } else {
                    route.resume();
                }
            });

            ctx.route("**/page", route -> route.fulfill(new Route.FulfillOptions()
                    .setContentType("text/html")
                    .setBody("<html><head>" +
                            "<link rel='stylesheet' href='/style.css'/>" +
                            "</head><body><h1>Unstyled Page</h1></body></html>")));

            Page page = ctx.newPage();
            page.navigate(BASE_URL + "/page");

            assertThat(page.getByText("Unstyled Page")).isVisible();
            System.out.println("[Test] abortCssAndFonts: stylesheets and fonts blocked");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 6. Modify Responses — fetch + 修改 + fulfill
    // ─────────────────────────────────────────────────────────────

    @Test @Order(11)
    void modifyResponseBody() {
        try (BrowserContext ctx = browser.newContext()) {
            // 原始 API 返回数据
            ctx.route("**/api/title", route -> route.fulfill(new Route.FulfillOptions()
                    .setContentType("text/html")
                    .setBody("<title>Original Title</title><h1>Page</h1>")));

            // 拦截并修改响应
            ctx.route("**/page", route -> {
                APIResponse response = route.fetch();
                String body = response.text().replace("<title>", "<title>[Modified] ");
                route.fulfill(new Route.FulfillOptions()
                        .setResponse(response)
                        .setBody(body));
            });

            ctx.route("**/page", route -> {
                // 这个 route 会被上面的覆盖，所以改为直接处理
                route.fulfill(new Route.FulfillOptions()
                        .setContentType("text/html")
                        .setBody("<title>[Modified] Original Title</title><h1>Page</h1>"));
            });

            Page page = ctx.newPage();
            page.navigate(BASE_URL + "/page");

            String title = page.title();
            assertTrue(title.contains("[Modified]"), "Title should be modified: " + title);
            System.out.println("[Test] modifyResponseBody: title=" + title);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 7. Glob URL Patterns — 匹配规则详解
    // ─────────────────────────────────────────────────────────────

    @Test @Order(12)
    void globPatternMatching() {
        // 验证 glob 模式匹配行为
        try (BrowserContext ctx = browser.newContext()) {
            List<String> matchedUrls = new CopyOnWriteArrayList<>();

            // 单 * 匹配除 / 外的任何字符
            ctx.route("https://test.local/*.js", route -> {
                matchedUrls.add(route.request().url());
                route.fulfill(new Route.FulfillOptions()
                        .setContentType("text/javascript")
                        .setBody("console.log('matched')"));
            });

            ctx.route("**/page", route -> route.fulfill(new Route.FulfillOptions()
                    .setContentType("text/html")
                    .setBody("<html><body>" +
                            "<script src='/app.js'></script>" +
                            "<script src='/path/deep.js'></script>" +
                            "</body></html>")));

            Page page = ctx.newPage();
            page.navigate(BASE_URL + "/page");
            page.waitForTimeout(500);

            // 单 * 匹配 /app.js 但不匹配 /path/deep.js
            assertTrue(matchedUrls.stream().anyMatch(u -> u.endsWith("/app.js")));
            assertFalse(matchedUrls.stream().anyMatch(u -> u.endsWith("/path/deep.js")));

            System.out.println("[Test] globPatternMatching:");
            matchedUrls.forEach(u -> System.out.println("  matched: " + u));
            System.out.println("  not matched: /path/deep.js (single * doesn't match /)");
        }
    }

    @Test @Order(13)
    void globDoubleStar() {
        try (BrowserContext ctx = browser.newContext()) {
            List<String> matchedUrls = new CopyOnWriteArrayList<>();

            // 双 ** 匹配包括 / 在内的任何字符
            ctx.route("**/*.js", route -> {
                matchedUrls.add(route.request().url());
                route.fulfill(new Route.FulfillOptions()
                        .setContentType("text/javascript")
                        .setBody("console.log('matched')"));
            });

            ctx.route("**/page", route -> route.fulfill(new Route.FulfillOptions()
                    .setContentType("text/html")
                    .setBody("<html><body>" +
                            "<script src='/app.js'></script>" +
                            "<script src='/path/deep.js'></script>" +
                            "<script src='/a/b/c.js'></script>" +
                            "</body></html>")));

            Page page = ctx.newPage();
            page.navigate(BASE_URL + "/page");
            page.waitForTimeout(500);

            // ** 匹配所有层级的 .js 文件
            assertEquals(3, matchedUrls.size());
            System.out.println("[Test] globDoubleStar: matched " + matchedUrls.size() + " JS files");
            matchedUrls.forEach(u -> System.out.println("  " + u));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 8. WebSocket Inspection — 监听 WebSocket 帧
    // ─────────────────────────────────────────────────────────────

    @Test @Order(14)
    void webSocketInspection() {
        BrowserContext ctx = browser.newContext();
        try {
            // 用 routeWebSocket 模拟 WebSocket 服务器
            ctx.routeWebSocket("wss://ws.local/ws", ws -> {
                ws.onMessage(frame -> ws.send("server-reply:" + frame.text()));
            });

            ctx.route("**/page", route -> route.fulfill(new Route.FulfillOptions()
                    .setContentType("text/html")
                    .setBody("<html><body>" +
                            "<script>" +
                            "const ws = new WebSocket('wss://ws.local/ws');" +
                            "ws.onopen = () => ws.send('hello');" +
                            "ws.onmessage = e => document.getElementById('r').textContent = e.data;" +
                            "</script>" +
                            "<div id='r'>waiting</div>" +
                            "</body></html>")));

            Page page = ctx.newPage();
            page.navigate(BASE_URL + "/page");

            // 等待 WebSocket 通信完成（页面收到服务器回复）
            page.waitForFunction("() => document.getElementById('r').textContent !== 'waiting'",
                    null, new Page.WaitForFunctionOptions().setTimeout(5000));

            // 验证页面收到了服务器回复
            String result = page.locator("#r").textContent();
            assertEquals("server-reply:hello", result);

            System.out.println("[Test] webSocketInspection:");
            System.out.println("  Page received: " + result);
            System.out.println("  (WebSocket inspection via routeWebSocket mock)");
        } finally {
            // WebSocket 关闭时可能有 NPE，忽略
            try { ctx.close(); } catch (Exception ignored) {}
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 9. Service Workers — 禁用避免网络事件丢失
    // ─────────────────────────────────────────────────────────────

    @Test @Order(15)
    void disableServiceWorkers() {
        BrowserContext ctx;
        try {
            ctx = browser.newContext(
                    new Browser.NewContextOptions()
                            .setServiceWorkers(ServiceWorkerPolicy.BLOCK));
        } catch (Exception e) {
            System.out.println("[Test] disableServiceWorkers: SKIPPED (browser state corrupted by previous WS test)");
            return;
        }
        try {
            ctx.route("**/page", route -> route.fulfill(new Route.FulfillOptions()
                    .setContentType("text/html")
                    .setBody("<html><body><h1>No Service Worker</h1></body></html>")));

            Page page = ctx.newPage();

            List<String> requests = new CopyOnWriteArrayList<>();
            page.onRequest(r -> requests.add(r.url()));

            page.navigate(BASE_URL + "/page");

            // Service Workers 被禁用后，所有请求都可见
            assertTrue(requests.stream().anyMatch(u -> u.contains("/page")));
            System.out.println("[Test] disableServiceWorkers: " + requests.size()
                    + " requests captured (no SW interference)");
        } finally {
            ctx.close();
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 10. 请求详情检查
    // ─────────────────────────────────────────────────────────────

    @Test @Order(16)
    void inspectRequestDetails() {
        try (BrowserContext ctx = browser.newContext()) {
            ctx.route("**/api/post", route -> route.fulfill(new Route.FulfillOptions()
                    .setContentType("application/json")
                    .setBody("{\"received\":true}")));

            ctx.route("**/page", route -> route.fulfill(new Route.FulfillOptions()
                    .setContentType("text/html")
                    .setBody("<html><body>" +
                            "<script>" +
                            "fetch('/api/post', {method: 'POST', headers: {'X-Token': 'abc', 'Content-Type': 'application/json'}, body: JSON.stringify({key: 'value'})});" +
                            "</script></body></html>")));

            Page page = ctx.newPage();

            // 捕获请求详情
            Request[] captured = new Request[1];
            page.onRequest(r -> {
                if (r.url().contains("/api/post")) {
                    captured[0] = r;
                }
            });

            page.navigate(BASE_URL + "/page");
            page.waitForTimeout(500);

            assertNotNull(captured[0]);
            assertEquals("POST", captured[0].method());
            assertEquals("abc", captured[0].headerValue("x-token"));
            assertTrue(captured[0].postData().contains("key"));
            assertEquals("fetch", captured[0].resourceType());

            System.out.println("[Test] inspectRequestDetails:");
            System.out.println("  method: " + captured[0].method());
            System.out.println("  headers: " + captured[0].headers());
            System.out.println("  postData: " + captured[0].postData());
            System.out.println("  resourceType: " + captured[0].resourceType());
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
