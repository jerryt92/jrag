package io.github.jerryt92.jrag.service.llm.tools.impl;

import com.alibaba.fastjson2.JSONObject;
import io.github.jerryt92.jrag.service.llm.tools.ToolInterface;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class LocalTimeGetter extends ToolInterface {

    public LocalTimeGetter() {
        toolInfo.setName("get_local_time")
                .setDescription("获取当前本地时间")
                .setParameters(
                        Collections.emptyList()
                );
    }

    @Override
    public List<String> apply(List<Map<String, Object>> requests) {
        List<String> results = new ArrayList<>();
        results.add(getCurrentLocalTime());
        return results;
    }

    private String getCurrentLocalTime() {
        TimeResult timeResult = new TimeResult();
        try {
            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            timeResult.currentTime = now.format(formatter);
            log.info("timeResult : " + JSONObject.toJSONString(timeResult));
            return JSONObject.toJSONString(timeResult);
        } catch (Exception e) {
            return "";
        }
    }

    @Data
    static class TimeResult {
        String currentTime;
    }
}