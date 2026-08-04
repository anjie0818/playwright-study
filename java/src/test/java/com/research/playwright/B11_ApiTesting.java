package com.research.playwright;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B11 - 官方文档 API testing 章节实践
 *
 * 本章核心：
 *   使用 APIRequestContext 直接发送 HTTP 请求，无需打开浏览器。
 *   本示例使用公开测试 API：https://jsonplaceholder.typicode.com/
 *
 * 运行方式：
 *   mvn test -Dtest=B11_ApiTesting
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class B11_ApiTesting {

    private static final String BASE_URL = "https://jsonplaceholder.typicode.com";

    private Playwright playwright;
    private APIRequestContext request;
    private final Gson gson = new Gson();

    @BeforeAll
    void beforeAll() {
        Map<String, String> env = new HashMap<>(System.getenv());
        env.put("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1");

        playwright = Playwright.create(new Playwright.CreateOptions().setEnv(env));

        request = playwright.request().newContext(new APIRequest.NewContextOptions()
                .setBaseURL(BASE_URL)
                .setExtraHTTPHeaders(Map.of(
                        "Accept", "application/json",
                        "Content-Type", "application/json"
                )));
    }

    @AfterAll
    void afterAll() {
        if (request != null) {
            request.dispose();
            request = null;
        }
        if (playwright != null) {
            playwright.close();
            playwright = null;
        }
    }

    @Test
    void shouldGetPost() {
        APIResponse response = request.get("/posts/1");
        assertTrue(response.ok(), "Response status: " + response.status());

        JsonObject post = gson.fromJson(response.text(), JsonObject.class);
        assertEquals(1, post.get("id").getAsInt());
        assertTrue(post.has("title"));
        assertTrue(post.has("body"));

        System.out.println("[Test] GET /posts/1 title=" + post.get("title").getAsString());
    }

    @Test
    void shouldCreatePost() {
        Map<String, String> data = new HashMap<>();
        data.put("title", "foo");
        data.put("body", "bar");
        data.put("userId", "1");

        APIResponse response = request.post("/posts",
                RequestOptions.create().setData(data));
        assertTrue(response.ok() || response.status() == 201,
                "Response status: " + response.status());

        JsonObject created = gson.fromJson(response.text(), JsonObject.class);
        assertEquals("foo", created.get("title").getAsString());
        assertEquals("bar", created.get("body").getAsString());
        assertTrue(created.has("id"));

        System.out.println("[Test] POST /posts created id=" + created.get("id").getAsInt());
    }

    @Test
    void shouldUpdatePost() {
        Map<String, String> data = new HashMap<>();
        data.put("id", "1");
        data.put("title", "updated title");
        data.put("body", "updated body");
        data.put("userId", "1");

        APIResponse response = request.put("/posts/1",
                RequestOptions.create().setData(data));
        assertTrue(response.ok(), "Response status: " + response.status());

        JsonObject updated = gson.fromJson(response.text(), JsonObject.class);
        assertEquals("updated title", updated.get("title").getAsString());
        assertEquals("updated body", updated.get("body").getAsString());
    }

    @Test
    void shouldDeletePost() {
        APIResponse response = request.delete("/posts/1");
        assertTrue(response.ok(), "Response status: " + response.status());
    }

    @Test
    void shouldFilterPostsByUser() {
        APIResponse response = request.get("/posts?userId=1");
        assertTrue(response.ok(), "Response status: " + response.status());

        com.google.gson.JsonArray posts = gson.fromJson(response.text(), com.google.gson.JsonArray.class);
        assertFalse(posts.isEmpty());

        posts.forEach(element -> {
            JsonObject post = element.getAsJsonObject();
            assertEquals(1, post.get("userId").getAsInt());
        });

        System.out.println("[Test] GET /posts?userId=1 count=" + posts.size());
    }
}
