package com.research.playwright;

import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.junit.Options;
import com.microsoft.playwright.junit.OptionsFactory;
import com.microsoft.playwright.junit.UsePlaywright;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B07 - 官方文档 JUnit (experimental) 章节实践
 *
 * 本章核心：
 *   使用 @UsePlaywright 注解，让 Playwright 自动注入 fixtures：
 *   - Page
 *   - BrowserContext
 *   - Browser
 *   - Playwright
 *   - APIRequestContext
 *
 *   通过 OptionsFactory 自定义 launch/context/api 配置。
 *
 * 运行方式：
 *   mvn test -Dtest=B07_JUnitExperimental
 */
@UsePlaywright(B07_JUnitExperimental.CustomOptions.class)
public class B07_JUnitExperimental {

    /**
     * 自定义 Playwright 配置
     */
    public static class CustomOptions implements OptionsFactory {
        @Override
        public Options getOptions() {
            return new Options()
                    // headless 模式，CI 友好
                    .setHeadless(true)
                    // 页面基础 URL
                    .setBaseUrl("https://playwright.dev")
                    // Context 级别配置
                    .setContextOptions(new Browser.NewContextOptions()
                            .setBaseURL("https://playwright.dev"))
                    // API 测试基础 URL
                    .setApiRequestOptions(new APIRequest.NewContextOptions()
                            .setBaseURL("https://playwright.dev"));
        }
    }

    @Test
    void shouldUseInjectedPage(Page page) {
        // 因为设置了 baseURL，可以直接用相对路径
        page.navigate("/");
        assertThat(page).hasTitle(Pattern.compile("Playwright"));
    }

    @Test
    void shouldClickButton(Page page) {
        page.navigate("data:text/html,<script>var result;</script><button onclick='result=\"Clicked\"'>Go</button>");
        page.locator("button").click();
        assertEquals("Clicked", page.evaluate("result"));
    }

    @Test
    void shouldCheckTheBox(Page page) {
        page.setContent("<input id='checkbox' type='checkbox'></input>");
        page.locator("input").check();
        assertTrue((Boolean) page.evaluate("window['checkbox'].checked"));
    }

    @Test
    void shouldUseApiRequestContext(APIRequestContext request) {
        // 使用注入的 APIRequestContext 做 API 测试
        APIResponse response = request.get("/");
        assertTrue(response.ok());
        String body = response.text();
        assertTrue(body.contains("Playwright"));
    }
}
