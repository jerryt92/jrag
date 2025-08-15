package io.github.jerryt92.jrag.service.llm.tools;

import io.github.jerryt92.jrag.model.ChatModel;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

@Slf4j
@Component
public class FunctionCallingService {
    @Getter
    private Map<String, ToolInterface> tools = new HashMap<>();

    @Autowired
    private ApplicationContext applicationContext;

    @PostConstruct
    public void init() {
        // 获取所有实现了ToolInterface接口的bean
        for (ToolInterface toolBean : applicationContext.getBeansOfType(ToolInterface.class).values()) {
            if (tools.containsKey(toolBean.toolInfo.getName())) {
                throw new RuntimeException("Duplicate tool name: " + toolBean.toolInfo.getName());
            }
            tools.put(toolBean.toolInfo.getName(), toolBean);
        }
        log.info("Loaded {} function calling tools", tools.size());
    }

    public Future<List<String>> functionCalling(ChatModel.ToolCall toolCall) {
        // 创建 FutureTask 来包装任务
        FutureTask<List<String>> futureTask = new FutureTask<>(() -> {
            log.info("FunctionCalling: {}", toolCall.getFunction().getName());
            log.info("FunctionCalling args: {}", toolCall.getFunction().getArguments());
            ToolInterface toolBean = tools.get(toolCall.getFunction().getName());
            if (toolBean == null) {
                String format = String.format("Tool %s not found", toolCall.getFunction().getName());
                log.error(format);
                return List.of(format);
            }
            return toolBean.apply(toolCall.getFunction().getArguments());
        });
        try {
            // 启动虚拟线程
            Thread virtualThread = Thread.startVirtualThread(futureTask);
            virtualThread.setName("FunctionCallingThread-" + toolCall.getFunction().getName());
        } catch (Throwable t) {
            log.error("", t);
        }
        return futureTask;
    }
}
