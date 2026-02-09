package io.github.jerryt92.jrag.utils;

import io.github.jerryt92.jrag.model.ChatModel;
import io.github.jerryt92.jrag.service.llm.client.LlmClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * 一系列基于LLM实现的功能
 */
@Slf4j
public class LlmBaseUtils {
    public static LlmClient llmClient;

    /**
     * 通过LLM修复json语法
     */
    public static String jsonSyntaxFix(String badJson) {
        log.info("LLM 尝试修复 JSON 语法");
        // 1. 构造 Prompt，强制要求纯文本 JSON
        String systemPrompt = """
                你是一个专业的 JSON 语法修复专家。用户提供的 JSON 字符串因特殊字符（如 SQL 中的引号）导致格式错误。\
                请分析意图，修复语法错误，并返回一个合法的标准 JSON 字符串。\
                注意：
                1. 严禁输出 Markdown 代码块（如 ```json）。
                2. 严禁输出任何解释性文字。
                3. 必须转义字符串内部的非结构性双引号。""";
        String userPrompt = "错误 JSON 数据：\n" + badJson;
        ChatModel.ChatRequest request = new ChatModel.ChatRequest()
                .setMessages(List.of(
                        new ChatModel.Message().setRole(ChatModel.Role.SYSTEM).setContent(systemPrompt),
                        new ChatModel.Message().setRole(ChatModel.Role.USER).setContent(userPrompt)
                ));
        ChatModel.ChatResponse chatResponse = llmClient.syncChat(request);
        try {
            String fixedContent = chatResponse.getMessage().getContent();
            // 2. 二次清洗：防止 LLM 还是不听话返回了 Markdown
            return cleanMarkdown(fixedContent);
        } catch (Exception e) {
            log.error("LLM 自我修复 JSON 失败", e);
            throw new RuntimeException("JSON 语法修复失败", e);
        }
    }

    private static String cleanMarkdown(String text) {
        if (StringUtils.isBlank(text)) return text;
        String clean = text.trim();
        if (clean.startsWith("```json")) {
            clean = clean.substring(7);
        } else if (clean.startsWith("```")) {
            clean = clean.substring(3);
        }
        if (clean.endsWith("```")) {
            clean = clean.substring(0, clean.length() - 3);
        }
        return clean.trim();
    }
}