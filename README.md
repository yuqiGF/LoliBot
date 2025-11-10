# Loli Bot

一个基于 Spring Boot 的 QQ 机器人项目，集成了爬虫、AI对话和视频生成等功能。

## 📦 项目结构

```
com.bot/
├── config/                  # 配置层 ⭐
│   ├── CorsConfig.java         # CORS 跨域配置
│   ├── DashScopeConfig.java    # DashScope AI 配置
│   └── RagConfig.java          # RAG 检索增强配置
│
├── controller/              # 控制器层
│   └── AiController.java       # AI 接口控制器
│
├── model/                   # 实体层 ⭐
│   └── Anime.java              # 番剧实体
│
├── plugin/                  # 插件层
│   └── MainPlugin.java         # QQ 机器人主插件
│
├── service/                 # 服务层
│   └── DashScopeService.java   # DashScope AI 服务接口
│
├── task/                    # 定时任务层
│   └── TodayAnime.java         # 今日新番推送任务
│
└── utils/                   # 工具层
    ├── common/              # 通用工具
    │   ├── HttpClientPool.java    # HTTP 连接池
    │   └── TextMatcher.java        # 文本相似度匹配
    ├── crawler/             # 爬虫模块
    │   ├── BangumiCrawler.java    # Bangumi 爬虫
    │   └── MoeGirlCrawler.java    # 萌娘百科爬虫
    └── ai/                  # AI 服务
        ├── DeepSeekClient.java    # DeepSeek 对话
        └── VideoGenerator.java    # 视频生成
```

## 🚀 快速开始

### 爬虫功能

```java
import com.bot.utils.crawler.*;

// Bangumi 番剧
String anime = BangumiCrawler.getTodayAnime();
String character = BangumiCrawler.searchCharacter("初音未来");

// 萌娘百科
String info = MoeGirlCrawler.getInfo("初音未来");
```

### AI 功能

```java
import com.bot.utils.ai.*;

// AI 对话
String reply = DeepSeekClient.chat("你好");

// 视频生成
String video = VideoGenerator.generate("跳舞", "imageUrl");
```

### 通用工具

```java
import com.bot.utils.common.*;

// HTTP 连接池
CloseableHttpClient client = HttpClientPool.createClient();

// 文本相似度
double similarity = TextMatcher.similarity("初音", "初音未来");
```

## 🧪 运行测试

```bash
mvn test
```

## 🔧 技术栈

- **框架**: Spring Boot
- **HTTP**: Apache HttpClient
- **爬虫**: Jsoup
- **JSON**: FastJSON2
- **AI**: LangChain4j + DashScope
- **测试**: JUnit 5

## ✨ 核心特性

- ✅ **连接池管理** - 高性能 HTTP 连接复用
- ✅ **智能重试** - 指数退避策略
- ✅ **相似度匹配** - Levenshtein 算法
- ✅ **模块化设计** - 清晰的代码结构
- ✅ **RAG 增强** - 检索增强生成
- ✅ **完善测试** - 单元测试覆盖

## 📊 项目特点

### 清晰的分层架构

- **config** - 配置集中管理
- **controller** - HTTP 接口
- **service** - 业务逻辑
- **plugin** - 机器人插件
- **utils** - 工具类
- **model** - 数据模型

### 优秀的代码质量

- ✅ 零冗余代码
- ✅ 规范的包命名
- ✅ 清晰的职责划分
- ✅ 完善的注释

## 📄 许可证

见 [LICENSE](LICENSE) 文件
