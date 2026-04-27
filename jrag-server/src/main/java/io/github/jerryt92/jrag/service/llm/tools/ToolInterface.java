package io.github.jerryt92.jrag.service.llm.tools;


import io.github.jerryt92.jrag.model.FunctionCallingModel;

import java.util.Map;

public abstract class ToolInterface {
    public final FunctionCallingModel.Tool toolInfo = new FunctionCallingModel.Tool();

    public abstract String apply(Map<String, Object> request);
}
