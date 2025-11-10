

# LoliBot Project Documentation

## Introduction

LoliBot is a multifunctional robot project based on Java, mainly targeting Chinese users. It integrates AI conversation, anime information query, and萌娘百科 (Anime Encyclopedia) query features, suitable for handling private and group messages on social platforms such as QQ and WeChat.

The project is developed using the Spring Boot framework combined with the DashScope AI interface and streaming message processing to provide an efficient interactive experience. Additionally, LoliBot supports enhanced AI responses using RAG (Retrieval-Augmented Generation) technology.

## Technology Stack

- Java 8+
- Spring Boot
- DashScope AI API
- Maven build tool
- Shiro permission control
- WebFlux + SSE (Server-Sent Events) for streaming responses

## Features

- 🤖 **AI Conversation**: Chat with DashScope's AI model via the `/ai/chat` interface.
- 🎬 **Today's New Animes**: Enter "今日新番" to get information about the latest anime releases for the current day.
- 📚 **Mengniang Encyclopedia**: Input `baka [character name]` to query information about a specific anime character.
- 💬 **Support for Group and Private Chats**: Supports group and private messages on QQ and WeChat (requires @bot in group messages).
- 🌐 **Cross-Origin Support**: Provides cross-origin access configuration through `CorsConfig`.
- 🧠 **RAG Enhanced Responses**: Combines vector and storage models to provide AI responses based on content retrieval.

## Usage Guide

### Group Chat Commands

| Command       | Functionality Description                        |
|---------------|--------------------------------------------------|
| `今日新番`    | Retrieve information about today's new animes      |
| `baka 角色名` | Query information about a specified character from the萌娘百科 |
| `loli`        | Chat with the AI model                           |
| `啾咪`        | Trigger private chat with the AI model for a reply |

### API Interface

Access the `/ai/chat` interface for AI conversations, supporting the following parameters:

- `memoryId`: ID for remembering conversation context
- `message`: Content of the message sent to the AI

## Environmental Dependencies

- DashScope API key
- QQ/WeChat Bot SDK (used with Shiro annotations for message handling)
- Redis (for storing memory context)

## Configuration Files

- `application.yml`: Main configuration file, including Spring Boot, DashScope, RAG, and database connection settings.
- `system-prompt.txt`: System prompt information for the AI model.

## Module Structure

- `controller`: AI conversation interface.
- `service`: AI service interface and factory classes, responsible for generating DashScopeService instances.
- `utils`: Utility classes, such as anime query,萌娘百科, and AI request encapsulation.
- `plugin`: Message handler plugins, containing logic for various bot commands.
- `config`: Spring configuration classes, such as CORS and RAG.

## Installation & Deployment

1. Install Maven and JDK 1.8+
2. Clone the project:
   ```bash
   git clone https://gitee.com/wang-guofu/loli-bot
   ```
3. Modify the `application.yml` configuration and add the DashScope API Key, Bot Token, and other necessary information.
4. Build and run the project:
   ```bash
   mvn spring-boot:run
   ```

## Test Cases

The project includes a comprehensive testing module:

- `LoliBotApplicationTests`: Main program tests to verify bot functionality.
- `DashScopeServiceTest`: AI service tests to ensure the AI interface returns correct results.
- `test.java`: Simple tests for utility classes.

## Contribution Guidelines

Pull requests or issues are welcome. Please follow these principles:

- Keep code concise and well-commented.
- Run test cases before submitting changes.
- Use meaningful naming and maintain clean code structure.

## License

This project is released under the [MIT License](LICENSE). For details, please refer to the `LICENSE` file in the project root directory.

## Contact

For any questions, visit the project homepage: [Gitee - LoliBot](https://gitee.com/wang-guofu/loli-bot) and submit an Issue or Pull Request.

---

🌟 A cute and intelligent bot, perfect for anime and AI enthusiasts! We hope you enjoy this project too~