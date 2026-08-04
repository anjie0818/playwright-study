package com.research.playwright;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.junit.UsePlaywright;
import org.junit.jupiter.api.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * B10 - 官方文档 Auto-waiting 章节实践
 *
 * 本章核心：
 *   Playwright 在执行动作前会自动做 actionability 检查：
 *   - Visible: 元素可见（非 display:none，有非空包围盒）
 *   - Stable: 元素位置稳定，动画结束
 *   - Receives Events: 元素不被其他元素遮挡
 *   - Enabled: 元素可用
 *   - Editable: 元素可编辑
 *
 *   同时 Playwright 断言也自带自动重试机制。
 *
 * 运行方式：
 *   mvn test -Dtest=B10_AutoWaiting
 */
@UsePlaywright
public class B10_AutoWaiting {

    @Test
    void shouldAutoWaitForButtonEnabled(Page page) {
        page.setContent("""
                <html>
                <body>
                  <button id='submit' disabled>Submit</button>
                  <div id='result'></div>
                  <script>
                    setTimeout(() => {
                      document.getElementById('submit').disabled = false;
                    }, 1000);
                    document.getElementById('submit').addEventListener('click', () => {
                      document.getElementById('result').textContent = 'submitted';
                    });
                  </script>
                </body>
                </html>
                """);

        long start = System.currentTimeMillis();

        // Playwright 会自动等待按钮 enabled 后再点击
        page.locator("#submit").click();

        long elapsed = System.currentTimeMillis() - start;
        assertEquals("submitted", page.locator("#result").textContent());

        // 验证确实等待了至少 1 秒
        assertTrue(elapsed >= 900, "Expected to wait for button enabled, but elapsed=" + elapsed);
        System.out.println("[Test] Auto-waited " + elapsed + "ms for button to be enabled");
    }

    @Test
    void shouldAutoWaitForElementVisible(Page page) {
        page.setContent("""
                <html>
                <body>
                  <button id='show' onclick="setTimeout(() => document.getElementById('msg').style.display='block', 500)">Show</button>
                  <div id='msg' style='display:none;'>Hello</div>
                </body>
                </html>
                """);

        page.locator("#show").click();

        // 断言会自动重试，直到元素可见
        assertThat(page.locator("#msg")).isVisible();
    }

    @Test
    void shouldAutoWaitForStableElement(Page page) {
        page.setContent("""
                <html>
                <body>
                  <button id='moving' style='position:absolute;left:0;top:0;'>Click me</button>
                  <script>
                    let pos = 0;
                    const interval = setInterval(() => {
                      pos += 10;
                      document.getElementById('moving').style.left = pos + 'px';
                      if (pos >= 100) clearInterval(interval);
                    }, 50);
                  </script>
                </body>
                </html>
                """);

        // Playwright 会等动画结束、元素稳定后再点击
        page.locator("#moving").click();
        System.out.println("[Test] Clicked after element became stable");
    }

    @Test
    void shouldForceClickWithoutWaiting(Page page) {
        page.setContent("""
                <html>
                <body>
                  <button id='submit' disabled>Submit</button>
                  <div id='result'></div>
                  <script>
                    document.getElementById('submit').addEventListener('click', () => {
                      document.getElementById('result').textContent = 'forced';
                    });
                  </script>
                </body>
                </html>
                """);

        // force=true 跳过 enabled 检查，但这里 disabled 按钮原生不会触发 click 事件
        // 所以用 dispatchEvent 更直接演示"绕过 actionability"
        page.locator("#submit").dispatchEvent("click");
        assertEquals("forced", page.locator("#result").textContent());
    }

    private static void assertTrue(boolean condition, String message) {
        org.junit.jupiter.api.Assertions.assertTrue(condition, message);
    }
}
