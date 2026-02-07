INSERT INTO user (id, username, password_hash, create_time, role)
VALUES ('a9e4a18b43ce44f2aece312667099a99', 'admin', '81279c81fe7d776409c3f1f3259b25ad3c055d3b78dc0fa29b463d7d73607c86',
        null, 1);
INSERT INTO user (id, username, password_hash, create_time, role)
VALUES ('3c0122c671b34137928aed221d5424fc', 'demo', '71346a6600bbc44255caa03d323e59e9b5deddb327917f8122fbb1fd5184161e',
        null, 2);

INSERT INTO ai_properties (property_name, property_value, description)
VALUES ('RETRIEVE_TOP_K', '5', '检索结果最大数量');

INSERT INTO ai_properties (property_name, property_value, description)
VALUES ('RETRIEVE_METRIC_TYPE', 'COSINE', '检索度量指标（COSINE余弦相似度、IP积内积、L2欧式距离等）');

INSERT INTO ai_properties (property_name, property_value, description)
VALUES ('RETRIEVE_METRIC_SCORE_COMPARE_EXPR', '> 0.7',
        '检索度量结果评分过滤条件，不同度量指标的取值范围不同，请根据实际情况修改');

INSERT INTO ai_properties (property_name, property_value, description)
VALUES ('RETRIEVE_DENSE_WEIGHT', '0.5', '稠密向量（语义）检索权重（0-1，占比）');

INSERT INTO ai_properties (property_name, property_value, description)
VALUES ('RETRIEVE_SPARSE_WEIGHT', '0.5', '稀疏向量（关键词）检索权重（0-1，占比）');

-- LLM (from DB: ai_properties)
INSERT INTO ai_properties (property_name, property_value, description)
VALUES ('llm-temperature', '0', 'LLM temperature');
INSERT INTO ai_properties (property_name, property_value, description)
VALUES ('llm-provider', 'open-ai', 'LLM provider（ollama/open-ai）');
INSERT INTO ai_properties (property_name, property_value, description)
VALUES ('llm-use-rag', 'true', '是否启用 RAG');
INSERT INTO ai_properties (property_name, property_value, description)
VALUES ('llm-use-tools', 'true', '是否启用工具调用');
INSERT INTO ai_properties (property_name, property_value, description)
VALUES ('llm-ollama-model-name', 'qwen3:14b-q8_0', 'Ollama 模型名称');
INSERT INTO ai_properties (property_name, property_value, description)
VALUES ('llm-ollama-base-url', 'http://172.16.8.107:11434', 'Ollama base url');
INSERT INTO ai_properties (property_name, property_value, description)
VALUES ('llm-ollama-keep-alive-seconds', '3600', 'Ollama 模型驻留时间（秒）');
INSERT INTO ai_properties (property_name, property_value, description)
VALUES ('llm-ollama-context-length', '32768', 'Ollama 上下文长度');
INSERT INTO ai_properties (property_name, property_value, description)
VALUES ('llm-open-ai-model-name', 'qwen-plus', 'OpenAI兼容模型名称');
INSERT INTO ai_properties (property_name, property_value, description)
VALUES ('llm-open-ai-base-url', 'https://dashscope.aliyuncs.com', 'OpenAI兼容 base url');
INSERT INTO ai_properties (property_name, property_value, description)
VALUES ('llm-open-ai-completions-path', '/compatible-mode/v1/chat/completions', 'OpenAI兼容 chat completions path');
INSERT INTO ai_properties (property_name, property_value, description)
VALUES ('llm-open-ai-key', 'llm-ollama-keep-alive-seconds', 'OpenAI兼容 API Key');
INSERT INTO ai_properties (property_name, property_value, description)
VALUES ('llm-open-ai-context-length', '32768', 'OpenAI 上下文长度');

-- Embedding (from DB: ai_properties)
INSERT INTO ai_properties (property_name, property_value, description)
VALUES ('embedding-embedding-provider', 'open-ai', 'Embedding provider（ollama/open-ai）');
INSERT INTO ai_properties (property_name, property_value, description)
VALUES ('embedding-ollama-model-name', 'nomic-embed-text:latest', 'Ollama embedding 模型名称');
INSERT INTO ai_properties (property_name, property_value, description)
VALUES ('embedding-ollama-base-url', 'http://127.0.0.1:11434', 'Ollama embedding base url');
INSERT INTO ai_properties (property_name, property_value, description)
VALUES ('embedding-ollama-keep_alive_seconds', '3600', 'Ollama embedding 模型驻留时间（秒）');
INSERT INTO ai_properties (property_name, property_value, description)
VALUES ('embedding-open-ai-model-name', 'text-embedding-v4', 'OpenAI兼容 embedding 模型名称');
INSERT INTO ai_properties (property_name, property_value, description)
VALUES ('embedding-open-ai-base-url', 'https://dashscope.aliyuncs.com', 'OpenAI兼容 embedding base url');
INSERT INTO ai_properties (property_name, property_value, description)
VALUES ('embedding-open-ai-embeddings-path', '/compatible-mode/v1/embeddings', 'OpenAI兼容 embeddings path');
INSERT INTO ai_properties (property_name, property_value, description)
VALUES ('embedding-open-ai-key', 'llm-ollama-keep-alive-seconds', 'OpenAI兼容 embedding API Key');
