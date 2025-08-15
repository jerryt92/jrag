package io.github.jerryt92.jrag.service.llm.tools;


import io.github.jerryt92.jrag.model.FunctionCallingModel;

import java.util.List;
import java.util.Map;

public abstract class ToolInterface {
    public final FunctionCallingModel.Tool toolInfo = new FunctionCallingModel.Tool();

    public abstract List<String> apply(List<Map<String, Object>> requests);
}
