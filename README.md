# LoliBot

LoliBot 是一个基于 Spring Boot 和 Mikuac Shiro 的 QQ 机器人项目，当前主要包含 AI 聊天、DeepSeek 临时问答、萌娘百科查询、视频生成、扫雷小游戏、WakaTime 编程统计等功能。

## 当前状态

- AI 聊天：使用 LangChain4j + DashScope，支持群聊 @ 回复、私聊回复、自动接话和好感度状态。
- DeepSeek 问答：群内使用 `ds 问题` 触发，API Key 从配置注入。
- 萌娘百科：使用爬虫获取条目信息，并可通过 Typst 渲染图片卡片。
- 视频生成：@ 机器人并带“生成视频 / 视频生成 / video”等关键词，同时附带图片，调用通义万相图生视频。
- 扫雷小游戏：管理员使用 `boom on/off` 控制开关，群内使用 `boom` 开局。
- WakaTime：使用 `waka` / `编程` 查询今日编程时间，使用 `waka week` / `编程周报` 查询近 7 天统计。
- 今日新番：该功能依赖 Bangumi；当前因网络环境可能不可用，本轮暂不维护。

## 项目结构

```text
src/main/java/com/bot
├── config        Spring、DashScope、RAG、跨域等配置
├── controller    Web API 入口
├── game          群内小游戏核心逻辑
├── guardrail     AI 输入护轨
├── model         简单数据模型
├── plugin        QQ 群聊/私聊插件入口
├── service       AI 服务接口
├── task          定时任务
└── utils         AI、爬虫、消息解析、Typst 渲染等工具
```

更详细的维护说明见 [项目结构与维护指南](docs/项目结构与维护指南.md)。

## 环境要求

- JDK 21+
- Maven 3.6+
- 可用的 QQ 机器人服务，例如 NapCat
- 可选：Typst，用于渲染扫雷棋盘和萌百信息卡

## 本地配置

复制模板：

```bash
cp src/main/resources/application-local.example.yml src/main/resources/application-local.yml
```

然后在 `application-local.yml` 中填写真实配置：

```yaml
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

deepseek:
  api-key: 你的_DeepSeek_API_Key
```

`application-local.yml` 已在 `.gitignore` 中忽略，不要提交真实密钥。

## 构建与运行

```bash
./mvnw clean package
java -jar target/loli_bot-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

Windows 下可使用：

```powershell
.\mvnw.cmd clean package
java -jar target\loli_bot-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

## 常用命令

- `@机器人 消息`：DashScope 聊天
- `好感度`：查看当前用户好感度
- `ds 问题`：DeepSeek 临时问答
- `baka 词条`：萌娘百科查询
- `boom on` / `boom off`：管理员开关扫雷
- `boom`：开始或查看扫雷棋盘
- `waka` / `编程`：今日编程统计
- `waka week` / `编程周报`：近 7 天编程统计

## 开发约定

- 插件入口放在 `com.bot.plugin`，只负责命令匹配、参数解析和消息发送。
- 外部 API 调用放在 `utils.ai` 或 `utils.crawler`，不要在插件里直接拼 HTTP。
- 多个插件共用的消息解析、Typst 渲染等逻辑放在 `utils.common`。
- API Key、QQ 号、群号等私密或本地化配置不要提交到仓库。
