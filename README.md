# LoliBot

LoliBot 是一个基于 Spring Boot、Mikuac Shiro 与 LangChain4j 的 QQ 群聊机器人。它以“琪琪”为聊天人格，提供带独立群上下文的 AI 聊天、群语录、明日方舟 PRTS 查询、动漫资料、百度百科、日语入门学习、WakaTime 统计和扫雷等功能。信息量较大的结果会通过 Typst 排版为图片。

## 功能概览

| 功能 | 指令示例 | 说明 |
| --- | --- | --- |
| 琪琪手册 | `琪琪手册` | 返回双栏图片版总功能说明 |
| AI 聊天 | `@琪琪 你好` | 所有群均支持主动 @；指定群支持独立上下文和自然接话 |
| 好感度 | `好感度` | 每个用户、每个群分别保存聊天档案 |
| 群复读 | 无需指令 | 每个群独立判断，所有群可用 |
| 群语录 | `add 啾咪` | 下一条消息保存为语录；支持文字、图片、表情包和多人同时添加 |
| PRTS | `prts 羽毛笔` | 查询干员、敌人、道具、时装、关卡、肉鸽、公招标签等资料 |
| 动漫资料 | `anime` | 查看最近更新；`anime 作品名` 查询详情并翻译简介、职员和角色信息 |
| 百度百科 | `baidu 初音未来` | 返回摘要、基础字段和右侧词条代表图 |
| 日语入门 | `moji 食べる` | 生成含假名、核心义、简单例句和常用变形的学习卡；`moji 吃饭` 可由中文查日语辞书形，单发 `moji` 随机学习高频词 |
| WakaTime | `waka` | 查询今日统计；`waka week` 查询最近七个自然日 |
| 萌娘百科 | `baka 琪露诺` | 查询萌娘百科词条 |
| 扫雷 | `boom` | 管理员使用 `boom on/off` 开关；坐标翻格，`f A1` 标记 |

> Bangumi“今日新番”受网络环境影响，当前不维护。DeepSeek 临时问答和闻声视频目前标记为暂不可用。

## 聊天设计

- 主动 @ 在所有群可用；高级自动接话仅在本地配置的群中启用。
- 会话键由“群号 + 用户号”组成，不同用户、不同群之间不会串记忆。
- 主人身份仅由可信的本地配置判断，不相信昵称、自称或提示词诱导。
- 自动接话会去除重复消息和低信息刷屏；没有真实讨论来源时，不会主动复述 KFC、疯狂星期四或“V我50”等高频梗。
- 群语录按群持久化，添加流程按“群 + 用户”隔离，因此支持多人并发操作。

## 项目结构

```text
src/main/java/com/bot
├── config        Spring、DashScope 和机器人身份配置
├── controller    Web API 入口
├── game          扫雷等游戏逻辑
├── guardrail     AI 输入护轨
├── model         业务数据模型
├── plugin        QQ 指令与消息入口
├── service       AI、聊天档案和语录存储
├── task          定时任务
└── utils
    ├── ai         外部 AI 客户端
    ├── common     消息解析与通用 Typst 渲染
    └── crawler    PRTS、动漫、百科、WakaTime 等数据客户端
```

更详细的维护说明见 [项目结构与维护指南](docs/项目结构与维护指南.md)。

## 环境要求

- JDK 21+
- Maven Wrapper（仓库已包含）
- NapCat 或其他兼容 OneBot 11 的 QQ 机器人服务
- Typst，用于生成图片卡片和扫雷棋盘
- 可访问所需数据源的网络环境

## 本地配置

复制配置模板：

```bash
cp src/main/resources/application-local.example.yml src/main/resources/application-local.yml
```

Windows PowerShell：

```powershell
Copy-Item src/main/resources/application-local.example.yml src/main/resources/application-local.yml
```

在 `application-local.yml` 中填写本机配置：

```yaml
bot:
  account-id: 0
  master-qq: 0
  bird-qq: 0
  bad-gay-qq: 0
  qiqi-qq: 0
  scheduled-group: 0
  advanced-groups: []

shiro:
  ws:
    server:
      enable: true
      url: ws://127.0.0.1:3001

langchain4j:
  community:
    dashscope:
      chat-model:
        api-key: 你的_DashScope_API_Key
      embedding-model:
        api-key: 你的_DashScope_API_Key
      streaming-chat-model:
        api-key: 你的_DashScope_API_Key

wakatime:
  timezone: Asia/Shanghai
  api-key: 你的_WakaTime_API_Key

typst:
  path: typst
  font-path: fonts
```

`0` 或空列表表示未配置相应身份或群。真实 QQ 号、群号、API Key 和 WebSocket 地址只应保存在 `application-local.yml` 中。

## 数据持久化

运行时数据默认写入工作目录的 `data/`：

- `data/chat-profiles.json`：按群与用户隔离的昵称、好感度和回复率。
- `data/quotes.json`：各群关键词与语录索引。
- `data/quote-images/`：语录中的本地化图片和表情资源。

`application-local.yml`、`data/`、构建目录和 IDE 文件均已加入 `.gitignore`，不会进入仓库。

## 构建与运行

Linux / macOS：

```bash
./mvnw clean package
java -jar target/loli_bot-0.0.1-SNAPSHOT.jar
```

Windows：

```powershell
.\mvnw.cmd clean package
java -jar target\loli_bot-0.0.1-SNAPSHOT.jar
```

应用默认使用 `local` Profile，并通过 `optional:application-local.yml` 加载本地配置。

## 测试

运行普通单元测试：

```bash
./mvnw test
```

真实外部接口测试默认关闭。准备好 WakaTime Key 和可访问外网的环境后可手动启用：

```bash
RUN_EXTERNAL_API_TESTS=true WAKATIME_API_KEY=你的_Key \
  ./mvnw -Dtest=ExternalApiSmokeTest test
```

外部测试会实际访问动漫、百度百科、WakaTime 等数据源，并验证 Typst PNG 能成功生成。

日语词典与学习卡可单独实测：`RUN_JAPANESE_EXTERNAL_TESTS=true ./mvnw -Dtest=JapaneseExternalApiSmokeTest test`。

## 开发约定

- 插件只负责指令匹配、参数解析和消息发送，数据抓取与持久化放在独立服务中。
- 复杂返回统一使用结构化卡片和 Typst，不在插件中重复拼接整套模板。
- 新增外部接口时应设置连接超时、响应校验和失败提示。
- 日语词条事实来自 [Jisho](https://jisho.org/) 所使用的 [JMdict](https://www.jmdict.org/jmdict/j_jmdict.html) 数据；中文说明和例句只承担教学整理，不覆盖词典事实。
- 不提交真实密钥、QQ 号、群号、聊天档案、语录图片或服务器信息。
- 今日新番功能暂不作为构建与发布的阻塞项。

## License

本项目使用 [MIT License](LICENSE)。
