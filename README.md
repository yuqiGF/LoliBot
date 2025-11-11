# LoliBot - QQ机器人项目

## 项目介绍
LoliBot是一个基于Spring Boot开发的多功能QQ机器人，集成了AI聊天、新番信息查询、视频生成等丰富功能，为QQ群提供智能化的互动体验。

## 功能特性

### 1. AI聊天功能
- 接入DeepSeek模型，提供可爱猫娘风格的对话体验
- 基于DashScope API的智能聊天，支持流式输出（权限校验暂时写在了prompt里，后续要用拦截器彻底优化💦）
- RAG(检索增强生成)功能，提升对话质量

### 2. 新番查询功能
- 自动获取每日新番信息，支持定时推送
- 提供番剧封面、评分等详细信息
- 支持群内命令查询今日新番

### 3. 视频生成功能
- 基于阿里云通义万相的视频生成能力
- 支持文本和图片输入，生成高质量短视频

### 4. 百科搜索功能
- 集成萌娘百科爬虫，快速获取百科知识
- 支持关键词搜索，返回结构化信息和图片

### 5. Web服务接口
- 提供RESTful API接口
- 支持AI流式聊天的Web端调用

## 技术栈

- **后端框架**: Spring Boot 3.5.4
- **机器人框架**: Mikuac Shiro 2.3.5
- **AI模型集成**: 
  - LangChain4j 1.1.0
  - DashScope API集成
  - DeepSeek API集成
- **网络爬虫**: 
  - Apache HttpClient 4.5.13
  - Jsoup 1.15.3
- **JSON处理**: Fastjson 1.2.83
- **构建工具**: Maven
- **Java版本**: JDK 21+

## 环境要求

- JDK 21或更高版本
- Maven 3.6+
- 可用的QQ机器人服务（如Napcat）
- 网络访问权限（用于API调用）

## 部署教程

### 1. 克隆项目

```bash
git clone https://github.com/your-username/LoliBot.git
cd LoliBot
```

### 2. 配置必要的API密钥

> ⚠️ 重要：以下敏感信息必须在本地配置，切勿提交到Git仓库！

复制模板配置文件并添加您的API密钥：

```bash
cp src/main/resources/application-local.example.yml src/main/resources/application-local.yml
```

编辑 `application-local.yml` 文件，填入以下内容：

```yaml
spring:
  application:
    name: loli_bot
  datasource:
    password: 123456  # 可选：如需数据库功能

# 本地开发配置
# 注意：此文件不应提交到git仓库
langchain4j:
  community:
    dashscope:
      chat-model:
        model-name: qwen3-max-preview
        api-key: 您的DashScope_API密钥
      embedding-model:
        model-name: text-embedding-v4
        api-key: 您的DashScope_API密钥
      streaming-chat-model:
        model-name: qwen3-max-preview
        api-key: 您的DashScope_API密钥

#deepseek配置
deepseek:
  api_key: 您的DeepSeek_API密钥
```

### 3. 构建项目

```bash
mvn clean package
```

### 4. 启动QQ机器人服务

在启动LoliBot之前，请确保您已启动QQ机器人服务（如Napcat，Napcat部署请参考 https://napneko.github.io/guide/napcat） 。参考配置：

```bash
# 以Napcat为例，在服务器上启动
xvfb-run -a /root/Napcat/opt/QQ/qq --no-sandbox

# 或使用screen后台运行
screen -S qq
xvfb-run -a /root/Napcat/opt/QQ/qq --no-sandbox
```

### 5. 运行LoliBot

```bash
java -jar target/loli_bot-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

### 6. 配置QQ机器人连接

在 `application.yml` 中配置WebSocket服务地址：

```yaml
shiro:
  ws:
    server:
      enable: true
      url: "ws://your-bot-server-address:port"  # 替换为实际的机器人WebSocket地址
```

## 使用说明

### 群聊命令

- **今日新番**: 发送"今日新番"获取当天的新番信息
- **DeepSeek AI聊天**: 使用 `ds 消息内容` 格式与DeepSeek模型对话
- **百科搜索**: 支持查询萌娘百科内容
- **视频生成**: 使用特定命令触发视频生成功能

### Web API使用

- **AI流式聊天接口**:
  ```
  GET /ai/chat?memoryId={用户ID}&message={消息内容}
  ```
  返回Server-Sent Events流，实现实时聊天体验

## 关键配置说明

### 配置文件说明

1. **application.yml**: 基础配置文件，不包含敏感信息
2. **application-local.yml**: 本地开发配置，包含API密钥等敏感信息，需自行创建

### 需要用户自行添加的内容

1. **DashScope API密钥**:
   - 从阿里云DashScope平台获取
   - 用于AI聊天和视频生成功能

2. **DeepSeek API密钥**:
   - 从DeepSeek AI平台获取
   - 用于DeepSeek模型的对话功能

3. **QQ机器人WebSocket地址**:
   - 根据您使用的QQ机器人服务配置
   - 用于与QQ客户端建立连接

## 注意事项

1. **敏感信息保护**:
   - `application-local.yml` 已添加到 `.gitignore`，请确保不提交包含API密钥的配置文件
   - 生产环境建议使用环境变量或其他安全方式管理密钥

2. **API调用限制**:
   - DashScope和DeepSeek API可能有调用频率限制，请合理使用
   - 视频生成功能可能产生较高费用，请留意API使用量

3. **依赖服务**:
   - 确保QQ机器人服务稳定运行
   - 定期检查API服务状态

## 开发指南

### 添加新功能

1. 在 `com.bot.plugin` 包下创建新的插件类，继承 `BotPlugin`
2. 使用 `@Shiro` 和 `@GroupMessageHandler` 等注解定义消息处理方法
3. 在插件类中实现具体的业务逻辑

### 定时任务

1. 在 `com.bot.task` 包下创建定时任务类
2. 使用 `@Scheduled` 注解定义执行周期

## 常见问题

1. **启动失败提示缺少API密钥**:
   - 确保已创建 `application-local.yml` 并配置了正确的API密钥
   - 启动时添加 `--spring.profiles.active=local` 参数

2. **机器人无响应**:
   - 检查QQ机器人服务是否正常运行
   - 验证WebSocket连接配置是否正确

3. **API调用失败**:
   - 确认API密钥有效
   - 检查网络连接和API服务状态

## 许可证

本项目采用MIT许可证。

## 联系方式

如有问题或建议，请通过GitHub Issues提交反馈。
或者QQ：2328441709 联系宇崎崎（虽然不太可能派上什么用处💦）
