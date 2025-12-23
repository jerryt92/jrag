[简体中文](README.md) | English

[![GitHub](https://img.shields.io/badge/GitHub-Jrag-blue?logo=github)](https://github.com/jerryt92/jrag)

Jrag is a RAG (Retrieval-Augmented Generation) and MCP tool integration platform based on Java Spring Boot. It aims to enhance the application capabilities of large language models in the Java ecosystem by combining retrieval, MCP tools, and generative AI model technologies. The platform supports integration with multiple mainstream large language model interfaces, including Ollama and OpenAI, and connects with Milvus and vector databases to provide efficient vector storage and retrieval services.

## Contributors

<a href="https://github.com/jerryt92/jrag/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=jerryt92/jrag" />
</a>

## Demo

[Data Communication Encyclopedia Assistant](https://jerryt92.github.io/data-communication-encyclopedia)

**Data Communication Encyclopedia Assistant**, based on Jrag, can answer various data communication related questions.

## Architecture

![architecture](assets/architecture.png)

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

## To Be Improved

- **Rerank**：Provide reranking capabilities to improve relevance.
- Streamable HTTP transport layer compatible with MCP protocol (awaiting Spring AI Release).
- **Knowledge Base Management**: Provide knowledge base management functions, supporting operations such as creation, import, export, and deletion of knowledge bases.

## SQLite Initialization

Execute the following commands in the jrag directory:

```shell
mkdir -p ~/jrag
cp ./sqlite/jrag.db ~/jrag/jrag.db
```

## Default Credentials

Username: admin  
Password: jrag@2025

## Frontend

```shell
rm -rf jrag-starter/src/main/resources/dist
git clone -b dist https://github.com/jerryt92/jrag-ui.git jrag-starter/src/main/resources/dist
```

[jrag-ui](https://github.com/jerryt92/jrag-ui)