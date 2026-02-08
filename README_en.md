[简体中文](README.md) | English

[![GitHub](https://img.shields.io/badge/GitHub-Jrag-blue?logo=github)](https://github.com/jerryt92/jrag)

Jrag is a RAG (Retrieval-Augmented Generation) and MCP tool integration platform based on Java Spring Boot. It aims to enhance the application capabilities of large language models in the Java ecosystem by combining retrieval, MCP tools, and generative AI model technologies. The platform supports integration with multiple mainstream large language model interfaces, including Ollama and OpenAI, and connects with Milvus and vector databases to provide efficient vector storage and retrieval services.

## Contributors

<a href="https://github.com/jerryt92/jrag/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=jerryt92/jrag" />
</a>

## Docker Quick Start

All Docker files are under `docker/`. The default setup starts Milvus (v2.6.9) and Jrag.

1. Pull all dependency images (optional)

```shell
docker pull maven:3.8.8-amazoncorretto-21-debian
docker pull eclipse-temurin:21-jre
docker pull alpine/git
docker pull milvusdb/milvus:v2.6.9
docker pull alpine:3.20
```

2. Deployment

```shell
docker compose -f docker/docker-compose.yml up -d --build
```

Configurable options in `docker/.env`:

- `JRAG_BASE_DIR`: host base directory for configs/data (default `~/jrag`)
- `COMPOSE_PROJECT_NAME`: container name prefix (default `jrag`)
- `UPDATE_UI`: pull latest UI `dist` from Git (`true`/`false`)
- `JRAG_UI_REPO`: UI repo URL (default `https://github.com/jerryt92/jrag-ui.git`)
- `JRAG_UI_BRANCH`: UI branch (default `dist`)

Access:

- UI: `http://localhost:30110/`
- Health check: `http://localhost:30110/v1/api/jrag/health-check`

Access host from inside containers:

- macOS/Windows: `host.docker.internal`
- Linux: `host.docker.internal` (Docker 20.10+ with `extra_hosts: ["host.docker.internal:host-gateway"]`)

## Demo

[Data Communication Encyclopedia Assistant](https://jerryt92.github.io/data-communication-encyclopedia)

**Data Communication Encyclopedia Assistant**, based on Jrag, can answer various data communication related questions.

## Architecture

![architecture](assets/architecture.png)

## Demo

![demo](assets/demo.gif)

## Purpose

So far, most open-source RAG platforms are implemented in Python. As a Java developer, I hope Jrag can be more suitable for Java developers' use, providing better LLM integration and application for Java developers.

## Features

- **Multi-model Support**: Compatible with Ollama and OpenAI-style interfaces, allowing flexible switching between different large language models.
- **Vector Database Integration**: Supports Milvus vector databases to meet performance requirements in different scenarios.
- **Function Calling**: Supports function calling, enabling LLMs to call APIs from other systems.
- **MCP Support**: Support MCP (Model Context Protocol) to standardize model tool calling.
- MCP Client interacts with LLM using Function Calling technology instead of Prompt, saving token consumption.
- **Java Ecosystem Optimization**: Designed specifically for Java developers to simplify the integration and application of RAG technology in Java projects.
- **JDK21**: Jrag is developed based on JDK21 and can use virtual threads to improve concurrent performance.
- **Knowledge Base Maintenance**: Provide knowledge base management functions, supporting operations such as adding, modifying, deleting, and hit testing of knowledge in the knowledge base.

## UI

The interface style is elegant and uses a glassy style, supporting dark mode.

![ui1](assets/ui/1.png)

![ui2](assets/ui/2.png)

![ui3](assets/ui/3.png)

## Knowledge Base Maintenance

![ui4](assets/ui/4.png)

![ui5](assets/ui/5.png)

![ui6](assets/ui/6.png)

![ui7](assets/ui/7.png)

## To Be Improved

- **Rerank**：Provide reranking capabilities to improve relevance.
- Streamable HTTP transport layer compatible with MCP protocol (awaiting Spring AI Release).
- **Knowledge Base Management**: Provide knowledge base management functions, supporting operations such as creation, import, export, and deletion of knowledge bases.

## Default Credentials

Username: admin  
Password: jrag@2025

## Frontend

```shell
rm -rf jrag-starter/src/main/resources/dist
git clone -b dist https://github.com/jerryt92/jrag-ui.git jrag-starter/src/main/resources/dist
```

[jrag-ui](https://github.com/jerryt92/jrag-ui)
