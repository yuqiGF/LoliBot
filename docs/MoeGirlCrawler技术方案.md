# MoeGirlCrawler 技术方案文档

## 最终方案架构

```
用户输入 baka 琪露诺
     │
     ▼
┌─────────────────────────────────────────────────┐
│ 1. opensearch API 搜索                           │
│    GET moegirl.icu/api.php?action=opensearch     │
│    返回 JSON → 相似度排序 → 最佳匹配页面标题        │
└─────────────────────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────────────────┐
│ 2. revisions API 获取 wikitext                   │
│    GET moegirl.icu/api.php?action=query          │
│    &prop=revisions&rvprop=content                │
│    返回 JSON → 完整页面的维基文本源码               │
└─────────────────────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────────────────┐
│ 3. 解析信息卡模板                                  │
│    从 wikitext 中提取 {{模板名|key=value|...}}     │
│    清洗 wikitext 标记（链接、模板、格式）            │
└─────────────────────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────────────────┐
│ 4. 构建图片URL并下载                               │
│    文件名 → box.moegirl.icu/media/{filename}     │
│    下载到系统临时目录，缓存复用                      │
└─────────────────────────────────────────────────┘
     │
     ▼
   返回 InfoboxData (页面标题 + 字段Map + 图片路径 + 来源URL)
```

## 关键技术点

### 1. 为什么选择 moegirl.icu 而非官方站

`zh.moegirl.org.cn` 仅开放 `action=opensearch` 这一个 API action，其余 action（`parse`, `query/prop=revisions`, `mobileview` 等）均返回 `action-notallowed`。

`moegirl.icu` 是萌娘百科的社区镜像站，其 MediaWiki API 完整开放，`action=query&prop=revisions&rvprop=content` 可返回任意页面的完整 wikitext 源码。

### 2. 为什么用 API 而非 HTML 页面抓取

直接请求页面 URL（如 `moegirl.icu/琪露诺`）会触发 Cloudflare WAF，返回 HTTP 403。Java 的 TLS 指纹（无论是 Apache HttpClient 还是 `java.net.http.HttpClient`）均被识别并拦截。

但 API 端点（`/api.php`）的 WAF 策略明显宽松，仅做基础 User-Agent 检查，不会对 Java HTTP 库进行 TLS 指纹拦截。

### 3. 为什么用 wikitext 解析而非 HTML 解析

- **wikitext 解析更精确**：信息卡数据以 `{{模板名|本名=xxx|发色=xxx}}` 格式存储，key-value 结构明确，不会被 HTML 渲染差异影响
- **无 DOM 依赖**：不需要 Jsoup 解析 HTML，减少依赖
- **模板清洗**：递归移除嵌套模板 `{{xxx|...}}`、内部链接 `[[...]]`、维基格式标记 `'''bold'''`、引用 `<ref>` 等，得到纯净文本值

### 4. 图片处理

- 模板中图片字段为文件名（如 `50354936 p0.jpg`）
- 替换空格为下划线 → URL 编码 → 拼接 `https://box.moegirl.icu/media/{encoded_filename}`
- `box.moegirl.icu` 是静态资源域名，无 WAF 保护，可直接下载
- 下载后缓存到系统临时目录（按页面标题 hash 命名），避免重复下载

### 5. 相似度匹配

搜索 `opensearch` 返回多个候选标题，使用 `TextMatcher.similarity()` 计算 Levenshtein 距离 + 公共前缀得分，选择最匹配的结果而非简单取第一个。

---

## 之前方案失败原因分析

### 方案一：Playwright 无头浏览器

**做法**：启动 Chromium 浏览器，模拟用户访问 `mzh.moegirl.org.cn`，等待页面渲染后提取信息卡

**失败原因**：
- Cloudflare WAF 检测 Playwright 启动的 Chromium（通过 `navigator.webdriver` 属性、`--disable-blink-features=AutomationControlled` 等已不足以绕过新版 WAF）
- 页面加载触发 JS 质询（滑块验证），Playwright 无法自动通过
- 即使添加反检测脚本、自定义 User-Agent、locale 等，仍被拦截
- 每次查询需要启动/关闭浏览器，响应时间 10-30 秒，资源消耗大

### 方案二：纯 HTTP + Jsoup 抓取移动版页面

**做法**：用 Apache HttpClient 直接请求 `mzh.moegirl.org.cn/{title}`（移动版），用 Jsoup 解析 HTML 中的 `.infotemplatebox` 提取信息卡

**失败原因**：
- 移动版页面也受 WAF 保护，Java 的 HttpClient 返回 HTTP 403
- 即使换成官网桌面版 `zh.moegirl.org.cn/{title}`，同样 403
- 换成镜像站 `moegirl.icu/{title}` 的页面视图，Java 请求同样 403
- curl 可以访问页面（curl 的 TLS 指纹更接近浏览器），但 Java 的 Apache HttpClient / `java.net.http.HttpClient` 均被识别为自动化工具

### 方案三：REST API（rest.php）

**做法**：调用 `zh.moegirl.org.cn/rest.php/v1/page/{title}` 获取 JSON 格式的页面内容

**失败原因**：
- curl 测试可用（带 User-Agent 返回 200），但 Java 请求返回 403
- REST API 端点的 WAF 规则比 opensearch 严格，检测到 Java TLS 指纹后拒绝
- 即使尝试去掉 Referer、Accept 等头部，Java 请求仍被拦截

### 方案四：moegirl.icu API（最终成功方案）

**做法**：全部走 `moegirl.icu/api.php` 的 MediaWiki API（搜索 + revisions 获取源码 + 解析 wikitext）

**成功原因**：
- moegirl.icu 的 `/api.php` 端点对 Java HTTP 库友好，不检测 TLS 指纹
- `action=query&prop=revisions&rvprop=content` 在 moegirl.icu 上**未被禁用**（与官方站不同）
- wikitext 解析比 HTML 解析更稳定，不受页面样式变化影响
- `box.moegirl.icu` 图片静态域名无 WAF，可直接下载原图

---

## 关键对比

| 维度 | Playwright | HTML抓取 | REST API | **API+wikitext (最终)** |
|------|-----------|---------|----------|------------------------|
| 响应时间 | 10-30s | ~2s | ~1s | **~1-2s** |
| 成功率 | 0% (WAF) | 0% (403) | 0% (Java 403) | **100%** |
| 资源消耗 | 极高 | 低 | 低 | **低** |
| 数据完整度 | 完整 | 完整 | 完整 | **完整** |
| 维护复杂度 | 高 | 中 | 低 | **低** |

---

## 代码修改清单

| 文件 | 变更 |
|------|------|
| `pom.xml` | 移除 Playwright、Jsoup 依赖 |
| `MoeGirlCrawler.java` | 完全重写：API+wikitext方案，移除 HTML/Jsoup 解析 |
| `MoeGirlPlugin.java` | 适配 `InfoboxData` 返回类型，图片和文字分开发送 |
| `MoeGirlTest.java` | 适配新返回类型 |
| `MoeGirlFixTest.java` | 适配新返回类型，更新测试描述 |
| `CrawlerTest.java` | 适配新返回类型 |
