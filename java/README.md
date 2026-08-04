# Playwright Java 学习项目

按 [Playwright for Java 官方文档](https://playwright.dev/java/docs/intro) 章节逐章实践，每个章节对应一个可运行的 Java 示例。

---

## 目录与代码映射

**重要性说明**（从 TestMasterAI 框架角度评估）：
- ★★★ 核心功能：框架日常使用，必须掌握
- ★★☆ 常用功能：特定场景必备，推荐掌握
- ★☆☆ 特定场景：按需学习，非必须

| 章节 | 官方文档 | 代码文件 | 重要性 | 说明 |
|---|---|---|:---:|---|
| 环境适配 | - | 所有 `B*` 类内联 `resolveChromePath()` | - | 探测本地 Chrome，设置 `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1` 跳过浏览器下载 |
| 01 Installation / Intro | [Installation](https://playwright.dev/java/docs/intro) | `B01_IntroScreenshot.java`（已删除） | - | 用户 rejected，未保留 |
| 02 Writing tests | [Writing tests](https://playwright.dev/java/docs/writing-tests) | `B02_WritingTests.java` | ★★★ | 使用 Locator、`assertThat` 自动重试断言、两个 Context 验证隔离 |
| 02b Context 隔离补充 | - | `B02b_ContextIsolationDemo.java` | ★★☆ | 演示 Context A/B/C 的隔离与 `storageState` 复用 |
| 03 Generating tests | [Codegen](https://playwright.dev/java/docs/codegen) | `B03_GeneratingTests.java` | ★☆☆ | 通过 `page.pause()` 打开 Playwright Inspector 录制生成代码 |
| 04 Running and debugging tests | [Running tests](https://playwright.dev/java/docs/running-tests) | `B04_RunningAndDebuggingTests.java` | ★★☆ | JUnit 5 生命周期：`@BeforeAll` 复用 Browser，`@BeforeEach` 隔离 Context/Page |
| 05 Trace viewer | [Trace viewer](https://playwright.dev/java/docs/trace-viewer) | `B05_TraceViewer.java` | ★★☆ | 录制 trace 到 `target/trace/trace.zip`，失败时可用于精确回放 |
| 06 Setting up CI | [CI](https://playwright.dev/java/docs/ci) | `.github/workflows/playwright.yml` | ★☆☆ | GitHub Actions + 官方 Docker 镜像，失败时上传 trace artifact |
| 07 Test Runners | [Test Runners](https://playwright.dev/java/docs/test-runners) | （跳过） | ★☆☆ | 第三方测试框架集成（Cucumber/SpecFlow），与 TestMasterAI 不直接相关 |
| 08 JUnit (experimental) | [JUnit](https://playwright.dev/java/docs/junit) | `B07_JUnitExperimental.java` | ★☆☆ | `@UsePlaywright` + `OptionsFactory` 自动注入 `Page`/`APIRequestContext` |
| 09 Accessibility testing | [Accessibility](https://playwright.dev/java/docs/accessibility-testing) | （用户选择跳过） | ★☆☆ | 集成 axe-core 做无障碍扫描 |
| 10 Actions | [Actions](https://playwright.dev/java/docs/input) | `B09_Actions.java` | ★★★ | fill/check/select/click/hover/press/upload/focus/drag/scroll 综合演示 |
| 11 Auto-waiting | [Auto-waiting](https://playwright.dev/java/docs/actionability) | `B10_AutoWaiting.java` | ★★★ | 演示 visible/stable/enabled 自动等待及断言重试 |
| 12 API testing | [API testing](https://playwright.dev/java/docs/api-testing) | `B11_ApiTesting.java` | ★★☆ | 使用 APIRequestContext 直接做 REST API 测试 |
| 13 Assertions | [Assertions](https://playwright.dev/java/docs/test-assertions) | `B12_Assertions.java` | ★★★ | assertThat auto-retrying 断言全覆盖：状态/文本/属性/计数/Page/API/否定/软断言 |
| 14 Authentication | [Authentication](https://playwright.dev/java/docs/auth) | `B13_Authentication.java` | ★★★ | 三种认证模式：每次登录 / storageState 持久化复用 / sessionStorage 手动恢复 |
| 15 Browsers | [Browsers](https://playwright.dev/java/docs/browsers) | （跳过） | ★☆☆ | 三大引擎切换 / channel 品牌浏览器，本机 WebKit 版本兼容问题，暂跳过 |
| 16 Clock | [Clock](https://playwright.dev/java/docs/clock) | `B15_Clock.java` | ★★☆ | setFixedTime / install+pauseAt+fastForward / 超时登出 / runFor 精确步进 |
| 17 Dialogs | [Dialogs](https://playwright.dev/java/docs/dialogs) | `B16_Dialogs.java` | ★★☆ | alert/confirm/prompt/beforeunload 弹窗处理与断言 |
| 18 Debugging Tests | [Debugging](https://playwright.dev/java/docs/debug) | `B17_DebuggingTests.java` | ★★☆ | PWDEBUG=1 调试模式、page.pause() 断点、Verbose API 日志、SlowMo 减速 |
| 19 Downloads | [Downloads](https://playwright.dev/java/docs/downloads) | `B18_Downloads.java` | ★★☆ | page.waitForDownload()、download.saveAs()、onDownload 事件处理 |
| 20 Emulation | [Emulation](https://playwright.dev/java/docs/emulation) | `B19_Emulation.java` | ★★☆ | Viewport/isMobile/Locale/Timezone/Permissions/Geolocation/ColorScheme/Media/Offline |
| 21 Evaluating JavaScript | [Evaluating](https://playwright.dev/java/docs/evaluating) | `B20_EvaluatingJavaScript.java` | ★★★ | page.evaluate() 返回类型、异步自动 await、参数传递、addInitScript 注入 |
| 22 Events | [Events](https://playwright.dev/java/docs/events) | `B21_Events.java` | ★★★ | waitForRequest/Response/Popup、onRequest 监听、onceDialog 一次性监听 |
| 23 Extensibility | [Extensibility](https://playwright.dev/java/docs/extensibility) | `B22_Extensibility.java` | ★☆☆ | Selectors.register() 自定义选择器引擎（tag=/data-testid=/text-contains=） |
| 24 Frames | [Frames](https://playwright.dev/java/docs/frames) | `B23_Frames.java` | ★★★ | frameLocator 链式操作、page.frame(name/URL)、嵌套 iframe 处理 |
| 25 Handles | [Handles](https://playwright.dev/java/docs/handles) | `B24_Handles.java` | ★☆☆ | JSHandle/ElementHandle 概念、boundingBox、Locator vs Handle 对比、生命周期 |
| 26 Isolation | [Isolation](https://playwright.dev/java/docs/browser-contexts) | `B25_Isolation.java` | ★★☆ | Cookie/SessionStorage 隔离、多用户场景、storageState 导出/导入、visited links 隔离 |
| 27 Locators | [Locators](https://playwright.dev/java/docs/locators) | `B26_Locators.java` | ★★★ | 7 种内置定位器(getByRole/Label/Placeholder/Text/AltText/Title/TestId) + CSS/XPath + 链式 + filter |
| 28 Mock APIs | [Mock APIs](https://playwright.dev/java/docs/mock) | `B27_MockApis.java` | ★★☆ | route 拦截/mock/abort、修改响应、HAR 录制/重放、WebSocket 模拟 |
| 29 Multithreading | [Multithreading](https://playwright.dev/java/docs/multithreading) | `B28_Multithreading.java` | ★★☆ | 线程安全规则、独立 Playwright 实例、事件分发、并发测试模式 |
| 30 Navigations | [Navigations](https://playwright.dev/java/docs/navigations) | `B29_Navigations.java` | ★★★ | navigate、load states、waitForURL、导航事件、重定向、本地文件 |
| 31 Network | [Network](https://playwright.dev/java/docs/network) | `B30_Network.java` | ★★★ | HTTP 认证、网络事件监控、请求/响应拦截、abort 请求、WebSocket mock |
| 32 Other locators | [Other locators](https://playwright.dev/java/docs/other-locators) | `B31_OtherLocators.java` | ★★☆ | CSS 伪类、XPath、nth、父元素、旧版 text=、ID/testId 简写 |
| 33 Pages | [Pages](https://playwright.dev/java/docs/pages) | `B32_Pages.java` | ★★☆ | 多标签页、新页面事件、弹窗处理 |
| 34 Page object models | [Page object models](https://playwright.dev/java/docs/pom) | `B33_PageObjectModels.java` | ★★☆ | 页面对象模式、封装复杂操作、流程导航 |
| 35 Screenshots | [Screenshots](https://playwright.dev/java/docs/screenshots) | `B34_Screenshots.java` | ★☆☆ | 页面截图、元素截图、全页截图、裁剪区域 |
| 36 Snapshot testing | [Snapshot testing](https://playwright.dev/java/docs/aria-snapshots) | `B35_SnapshotTesting.java` | ★★☆ | ARIA 快照匹配（⚠️ @Disabled - API 在 1.56.0 中不可用） |
| 37 Test generator | [Test generator](https://playwright.dev/java/docs/codegen) | （跳过） | ★☆☆ | codegen CLI 录制工具，与 B03/B17 重叠 |
| 38 Touch events | [Touch events](https://playwright.dev/java/docs/touch-events) | `B37_TouchEvents.java` | ★☆☆ | 触摸手势模拟（tap、swipe、pinch） |
| 39 Trace viewer (高级) | [Trace viewer](https://playwright.dev/java/docs/trace-viewer) | （跳过） | ★★☆ | Trace viewer 详细用法，与 B05 基础版重叠 |
| 40 Videos | [Videos](https://playwright.dev/java/docs/videos) | `B38_Videos.java` | ★☆☆ | 视频录制（recordVideoDir、recordVideoSize） |
| 41 WebView2 | [WebView2](https://playwright.dev/java/docs/webview2) | `B39_WebView2.java` | ★☆☆ | WebView2 自动化（⚠️ @DisabledOnOs(MAC,LINUX) - 仅 Windows） |

---

## 快速开始

### 1. 运行单个示例

```bash
cd /Users/anjie/work/auto-api-test/testmaster-ai/research/playwright

# main 类示例
mvn compile exec:java -Dexec.mainClass="com.research.playwright.B02_WritingTests"

# JUnit 测试
mvn test -Dtest=B04_RunningAndDebuggingTests
```

### 2. 运行全部测试

```bash
mvn test
```

### 3. 查看 Trace

```bash
mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="show-trace target/trace/trace.zip"
```

---

## 环境说明

- **JDK**: 21
- **Maven**: 3.8+
- **Playwright**: 1.56.0
- **JUnit**: 5.10.2
- **浏览器策略**: 优先探测本地 Chrome，未找到则使用 Playwright 默认下载逻辑

本地 Chrome 探测路径（`resolveChromePath()`）：
- macOS: `/Applications/Google Chrome.app/Contents/MacOS/Google Chrome`
- Linux: `/usr/bin/google-chrome`、`/usr/bin/chromium-browser`
- Windows: `C:/Program Files/Google/Chrome/Application/chrome.exe`

也可通过环境变量覆盖：
```bash
export CHROME_EXECUTABLE_PATH=/path/to/your/chrome
```

---

## 关键设计原则

1. **不用 `PlaywrightHelper`**：每个示例内联浏览器初始化和 Chrome 探测逻辑，直接使用原生 Playwright API。
2. **Browser 复用，Context 隔离**：Browser 在类级或全局复用；每个测试方法使用独立的 `BrowserContext`/`Page`。
3. **稳定优先**：外部网站不稳定的测试（如 Wikipedia）替换为 `data URL` 或 route 拦截本地页面。
4. **有头模式优先**：默认使用 `setHeadless(false)`，便于观察执行过程；仅在 B05 TraceViewer 等需要 CI 环境的场景使用 headless 模式。
5. **路由拦截为主**：大部分测试使用 `BrowserContext.route()` 拦截请求返回本地 HTML，避免依赖外部网站。
6. **版本适配**：遇到 API 不可用（如 `matchesAriaSnapshot` 在 1.56.0 中不存在）时，使用 `@Disabled` 标记并说明原因。
7. **平台适配**：遇到平台特定功能（如 WebView2 仅 Windows）时，使用 `@DisabledOnOs` 标记。

---

## 文件结构

```
research/playwright/
├── pom.xml                                    # Maven 配置
├── README.md                                  # 本文件
├── .github/workflows/playwright.yml           # GitHub Actions CI
└── src/
    ├── main/java/com/research/playwright/
    │   ├── B02_WritingTests.java              # Writing tests 章节
    │   ├── B02b_ContextIsolationDemo.java     # Context 隔离补充
    │   └── B03_GeneratingTests.java           # Codegen 录制
    └── test/java/com/research/playwright/
        ├── B04_RunningAndDebuggingTests.java  # JUnit 生命周期
        ├── B05_TraceViewer.java               # Trace 录制
        ├── B07_JUnitExperimental.java         # @UsePlaywright 实验性集成
        ├── B09_Actions.java                   # 页面操作 Actions
        ├── B10_AutoWaiting.java               # 自动等待 Auto-waiting
        ├── B11_ApiTesting.java                # API 测试
        ├── B12_Assertions.java                # Assertions 断言
        ├── B13_Authentication.java            # Authentication 认证
        ├── B15_Clock.java                     # Clock 时间控制
        ├── B16_Dialogs.java                   # Dialogs 弹窗处理
        ├── B17_DebuggingTests.java            # 调试模式
        ├── B18_Downloads.java                 # 下载处理
        ├── B19_Emulation.java                 # 设备模拟
        ├── B20_EvaluatingJavaScript.java      # JS 执行
        ├── B21_Events.java                    # 事件处理
        ├── B22_Extensibility.java             # 自定义选择器
        ├── B23_Frames.java                    # iframe 处理
        ├── B24_Handles.java                   # JSHandle/ElementHandle
        ├── B25_Isolation.java                 # Context 隔离
        ├── B26_Locators.java                  # 定位器
        ├── B27_MockApis.java                  # API 模拟
        ├── B28_Multithreading.java            # 多线程
        ├── B29_Navigations.java               # 导航
        ├── B30_Network.java                   # 网络操作
        ├── B31_OtherLocators.java             # 其他定位器
        ├── B32_Pages.java                     # 多页面管理
        ├── B33_PageObjectModels.java          # POM 模式
        ├── B34_Screenshots.java               # 截图
        ├── B35_SnapshotTesting.java           # 快照测试
        ├── B37_TouchEvents.java               # 触摸事件
        ├── B38_Videos.java                    # 视频录制
        └── B39_WebView2.java                  # WebView2 自动化
```