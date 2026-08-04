package com.research.playwright;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.junit.UsePlaywright;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.MouseButton;
import com.microsoft.playwright.options.SelectOption;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B09 - 官方文档 Actions 章节实践
 *
 * 本章核心：
 *   Playwright 与页面元素的各种交互操作：fill、check、select、click、hover、press、upload、focus、drag、scroll。
 *   所有动作都内置自动等待（auto-waiting）。
 *
 * 运行方式：
 *   mvn test -Dtest=B09_Actions
 */
@UsePlaywright
public class B09_Actions {

    private static final String TEST_HTML = """
            <html>
            <body>
              <input id='username' type='text' data-testid='username'/>
              <input id='agree' type='checkbox'/><label for='agree'>I agree</label>
              <input id='newsletter' type='checkbox' checked/><label for='newsletter'>Subscribe</label>
              <select id='color'>
                <option value='red'>Red</option>
                <option value='green'>Green</option>
                <option value='blue'>Blue</option>
              </select>
              <button id='btn' data-testid='action-button'>Click me</button>
              <div id='box' style='width:100px;height:100px;background:#ccc;margin:20px;'>Hover me</div>
              <textarea id='bio' rows='3'></textarea>
              <input id='file' type='file'/>
              <div id='source' draggable='true' style='width:50px;height:50px;background:red;'>drag</div>
              <div id='target' style='width:100px;height:100px;background:blue;'>drop here</div>
              <div id='result'></div>
              <div id='spacer' style='height:500px;'></div>
              <button id='bottom-btn'>Bottom</button>
              <script>
                document.getElementById('btn').addEventListener('click', () => {
                  document.getElementById('result').textContent = 'clicked';
                });
                document.getElementById('box').addEventListener('mouseenter', () => {
                  document.getElementById('result').textContent = 'hovered';
                });
                document.getElementById('source').addEventListener('dragstart', (e) => {
                  e.dataTransfer.setData('text', 'dragged');
                });
                document.getElementById('target').addEventListener('drop', (e) => {
                  e.preventDefault();
                  document.getElementById('result').textContent = e.dataTransfer.getData('text');
                });
                document.getElementById('target').addEventListener('dragover', (e) => e.preventDefault());
              </script>
            </body>
            </html>
            """;

    @Test
    void shouldFillTextInput(Page page) {
        page.setContent(TEST_HTML);

        page.getByTestId("username").fill("Peter");
        assertEquals("Peter", page.getByTestId("username").inputValue());
    }

    @Test
    void shouldCheckAndUncheckBox(Page page) {
        page.setContent(TEST_HTML);

        Locator agree = page.locator("#agree");
        Locator newsletter = page.locator("#newsletter");

        agree.check();
        assertThat(agree).isChecked();

        newsletter.uncheck();
        assertThat(newsletter).not().isChecked();
    }

    @Test
    void shouldSelectOption(Page page) {
        page.setContent(TEST_HTML);

        page.locator("#color").selectOption("blue");
        assertEquals("blue", page.locator("#color").inputValue());

        page.locator("#color").selectOption(new SelectOption().setLabel("Green"));
        assertEquals("green", page.locator("#color").inputValue());
    }

    @Test
    void shouldClickAndHover(Page page) {
        page.setContent(TEST_HTML);

        // 普通点击
        page.getByTestId("action-button").click();
        assertEquals("clicked", page.locator("#result").textContent());

        // 右键点击
        page.getByTestId("action-button").click(new Locator.ClickOptions().setButton(MouseButton.RIGHT));

        // 悬停
        page.locator("#box").hover();
        assertEquals("hovered", page.locator("#result").textContent());
    }

    @Test
    void shouldTypeSequentiallyAndPressKeys(Page page) {
        page.setContent(TEST_HTML);

        Locator bio = page.locator("#bio");
        bio.pressSequentially("Hello World!");
        assertEquals("Hello World!", bio.inputValue());

        // 先选中全部内容，再按 Delete 清空
        bio.evaluate("e => e.select()");
        bio.press("Delete");
        assertEquals("", bio.inputValue());
    }

    @Test
    void shouldUploadFile(Page page) throws Exception {
        page.setContent(TEST_HTML);

        Path tempFile = Files.createTempFile("upload-", ".txt");
        Files.writeString(tempFile, "this is test", StandardCharsets.UTF_8);

        page.locator("#file").setInputFiles(tempFile);

        // 验证文件已选择（不同浏览器返回值略有差异，这里只验证非空）
        String fileValue = page.locator("#file").inputValue();
        assertTrue(fileValue.contains("upload-") || fileValue.endsWith(".txt"));

        Files.deleteIfExists(tempFile);
    }

    @Test
    void shouldDragAndDrop(Page page) {
        page.setContent(TEST_HTML);

        page.locator("#source").dragTo(page.locator("#target"));
        assertEquals("dragged", page.locator("#result").textContent());
    }

    @Test
    void shouldScrollIntoView(Page page) {
        page.setContent(TEST_HTML);

        // 元素在视口外，点击前会自动滚动到可见区域
        page.locator("#bottom-btn").click();
        assertThat(page.locator("#bottom-btn")).isInViewport();
    }
}
