# Playwright 学习项目

按 [Playwright 官方文档](https://playwright.dev) 章节逐章实践，包含两种语言版本。

## 目录结构

```
playwright-study/
├── java/          # Playwright Java 版（Maven + JDK 21）
│   ├── README.md  # Java 版章节清单与说明
│   ├── pom.xml
│   ├── src/
│   └── .github/workflows/playwright.yml
└── node/          # Playwright Node.js 版（待学习）
```

## Java 版

已完成 38 个章节实践，详见 [`java/README.md`](java/README.md)。

```bash
cd java
mvn test                                    # 运行全部测试
mvn test -Dtest=B04_RunningAndDebuggingTests  # 运行单个测试
```

## Node.js 版

待学习，目录预留。
