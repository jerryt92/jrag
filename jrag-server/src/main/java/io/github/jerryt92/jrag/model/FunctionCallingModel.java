package io.github.jerryt92.jrag.model;

import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.ai.model.ModelOptionsUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FunctionCallingModel {
    @Data
    @Accessors(chain = true)
    public static class Tool {
        private String name;
        private String description;
        private List<Parameter> parameters;

        @Data
        @Accessors(chain = true)
        public static class Parameter {
            private String name;
            private String type;
            private String description;
            private boolean required;
        }
    }

    @Data
    @Accessors(chain = true)
    public static class ToolResponse {
        String name;
        List<String> responseData;
    }

    public static ChatModel.Message buildToolResponseMessage(Collection<ToolResponse> toolResponses) {
        return new ChatModel.Message()
                .setRole(ChatModel.Role.TOOL)
                .setContent(ModelOptionsUtils.toJsonString(toolResponses));
    }

    public static Map<String, Object> generateToolParameters(List<Tool.Parameter> parameters) {
        Map<String, Object> properties = new HashMap<>();
        List<String> required = new ArrayList<>();
        for (Tool.Parameter parameter : parameters) {
            properties.put(
                    parameter.getName(),
                    Map.of(
                            "type", parameter.getType(),
                            "description", parameter.getDescription()
                    )
            );
            if (parameter.isRequired()) {
                required.add(parameter.getName());
            }
        }
        return Map.of(
                "type", "object",
                "properties", properties,
                "required", required
        );
    }
}
