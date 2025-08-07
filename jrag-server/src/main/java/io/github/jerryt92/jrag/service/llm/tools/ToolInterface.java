package io.github.jerryt92.jrag.service.llm.tools;


import io.github.jerryt92.jrag.model.FunctionCallingModel;

import java.util.Map;

public interface ToolInterface {
    FunctionCallingModel.Tool getToolInfo();

    String apply(Map<String, Object> request);
}
