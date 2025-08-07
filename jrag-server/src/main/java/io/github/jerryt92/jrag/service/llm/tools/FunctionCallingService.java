package io.github.jerryt92.jrag.service.llm.tools;

import io.github.jerryt92.jrag.model.ChatModel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
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
            tools.put(toolBean.getToolInfo().getName(), toolBean);
        }
        log.info("Loaded {} tools", tools.size());
    }

    public Future<String> functionCalling(ChatModel.ToolCall toolCall) {
        // 创建 FutureTask 来包装任务
        FutureTask<String> futureTask = new FutureTask<>(() -> {
            log.info("FunctionCalling: {}", toolCall.getFunction().getName());
            ToolInterface toolBean = tools.get(toolCall.getFunction().getName());
            if (toolBean == null) {
                log.error("Tool {} not found", toolCall.getFunction().getName());
                return null;
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
