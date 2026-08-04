package com.research.playwright;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * B16 - 官方文档 Dialogs 章节实践
 *
 * 本章核心：
 *   Playwright 默认会自动 dismiss 所有 alert/confirm/prompt 弹窗，
 *   但如果测试需要与弹窗交互，必须用 page.onDialog() 注册处理器。
 *
 *   HTML 页面放在 src/test/resources/pages/dialogs.html，
 *   方便独立修改页面内容后重新运行测试。
 *
 * 运行方式：
 *   mvn test -Dtest=B16_Dialogs
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class B16_Dialogs {

    private static final String DIALOG_URL = "http://test.local/dialogs";

    private Playwright playwright;
    private Browser browser;

    @BeforeAll
    void beforeAll() {
        Map<String, String> env = new HashMap<>(System.getenv());
        env.put("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1");
        playwright = Playwright.create(new Playwright.CreateOptions().setEnv(env));

        String chromePath = resolveChromePath();
        BrowserType.LaunchOptions opts = new BrowserType.LaunchOptions().setHeadless(true);
        if (chromePath != null) opts.setExecutablePath(java.nio.file.Paths.get(chromePath));
        browser = playwright.chromium().launch(opts);
        System.out.println("[Setup] Browser launched.");
    }

    @AfterAll
    void afterAll() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    /**
     * 从 classpath 读取 HTML 页面内容。
     * 资源文件路径：src/test/resources/pages/dialogs.html
     */
    private static String readHtml(String resourceName) {
        try (var is = B16_Dialogs.class.getClassLoader().getResourceAsStream("pages/" + resourceName)) {
            if (is == null) {
                throw new RuntimeException("Resource not found: pages/" + resourceName);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read resource: pages/" + resourceName, e);
        }
    }

    private BrowserContext newCtxWithRoute() {
        BrowserContext ctx = browser.newContext();
        String html = readHtml("dialogs.html");
        ctx.route(DIALOG_URL, r -> r.fulfill(
            new Route.FulfillOptions().setContentType("text/html").setBody(html)));
        return ctx;
    }

    // ─────────────────────────────────────────────────────────────
    // 1. alert 弹窗：默认自动 dismiss，但可注册 handler 获取 message
    // ─────────────────────────────────────────────────────────────

    /**
     * alert 只有一个"确定"按钮。
     * 通过 onDialog 注册处理器，可以拿到弹窗文案并验证。
     */
    @Test
    void handleAlert() {
        try (BrowserContext ctx = newCtxWithRoute()) {
            Page page = ctx.newPage();
            page.navigate(DIALOG_URL);

            List<String> messages = new ArrayList<>();
            page.onDialog(dialog -> {
                messages.add(dialog.message());
                dialog.accept();
            });

            page.locator("#btn-alert").click();

            org.junit.jupiter.api.Assertions.assertEquals("Saved successfully", messages.get(0));
            System.out.println("[Test] handleAlert passed. message=" + messages.get(0));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 2. confirm 弹窗：accept（确定）/ dismiss（取消）
    // ─────────────────────────────────────────────────────────────

    @Test
    void handleConfirmAccept() {
        try (BrowserContext ctx = newCtxWithRoute()) {
            Page page = ctx.newPage();
            page.navigate(DIALOG_URL);

            page.onDialog(Dialog::accept);
            page.locator("#btn-confirm").click();

            assertThat(page.locator("#result")).hasText("confirmed");
            System.out.println("[Test] handleConfirmAccept passed.");
        }
    }

    @Test
    void handleConfirmDismiss() {
        try (BrowserContext ctx = newCtxWithRoute()) {
            Page page = ctx.newPage();
            page.navigate(DIALOG_URL);

            page.onDialog(Dialog::dismiss);
            page.locator("#btn-confirm").click();

            assertThat(page.locator("#result")).hasText("cancelled");
            System.out.println("[Test] handleConfirmDismiss passed.");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 3. prompt 弹窗：accept(text) 输入文本
    // ─────────────────────────────────────────────────────────────

    @Test
    void handlePrompt() {
        try (BrowserContext ctx = newCtxWithRoute()) {
            Page page = ctx.newPage();
            page.navigate(DIALOG_URL);

            page.onDialog(dialog -> {
                org.junit.jupiter.api.Assertions.assertEquals("What is your name?", dialog.message());
                dialog.accept("Alice");
            });

            page.locator("#btn-prompt").click();
            assertThat(page.locator("#result")).hasText("Hello, Alice");

            System.out.println("[Test] handlePrompt passed.");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 4. beforeunload 弹窗：关闭页面前的确认
    // ─────────────────────────────────────────────────────────────

    /**
     * beforeunload 弹窗在页面关闭前弹出。
     *
     * 注意：headless Chromium 下 beforeunload dialog 行为不稳定，
     *      本测试默认跳过。若要在 headed 模式或真实 Chrome 下验证，
     *      可移除 @Disabled 注解。
     */
    @Test
    @org.junit.jupiter.api.Disabled("beforeunload dialog is unreliable in headless Chromium")
    void handleBeforeUnload() {
        try (BrowserContext ctx = newCtxWithRoute()) {
            Page page = ctx.newPage();
            page.navigate(DIALOG_URL);

            page.evaluate("() => { window.addEventListener('beforeunload', e => { e.preventDefault(); e.returnValue = ''; }); }");

            List<String> dialogTypes = new ArrayList<>();
            page.onDialog(dialog -> {
                dialogTypes.add(dialog.type());
                dialog.dismiss();
            });

            page.close(new Page.CloseOptions().setRunBeforeUnload(true));

            org.junit.jupiter.api.Assertions.assertEquals("beforeunload", dialogTypes.get(0));
            System.out.println("[Test] handleBeforeUnload passed. dialogType=" + dialogTypes.get(0));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 5. 多个弹窗按顺序处理
    // ─────────────────────────────────────────────────────────────

    @Test
    void handleMultipleDialogs() {
        try (BrowserContext ctx = newCtxWithRoute()) {
            Page page = ctx.newPage();
            page.navigate(DIALOG_URL);

            page.evaluate("() => { document.getElementById('btn-alert').onclick = () => { alert('first'); alert('second'); }; }");

            List<String> messages = new ArrayList<>();
            page.onDialog(dialog -> {
                messages.add(dialog.message());
                dialog.accept();
            });

            page.locator("#btn-alert").click();

            org.junit.jupiter.api.Assertions.assertEquals(List.of("first", "second"), messages);
            System.out.println("[Test] handleMultipleDialogs passed. messages=" + messages);
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
        };
        for (String p : candidates) {
            if (new java.io.File(p).exists()) return p;
        }
        return null;
    }
}
