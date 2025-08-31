package io.github.jerryt92.jrag.service.llm.tools.impl;

import io.github.jerryt92.jrag.model.FunctionCallingModel;
import io.github.jerryt92.jrag.service.llm.tools.ToolInterface;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class DataCommunicationEncyclopedia extends ToolInterface {
    private final RestTemplate restTemplate;

    private static String url = "https://info.support.huawei.com/info-finder/encyclopedia/zh/detail?action=queryMatchedEntityDetail&keyword=";

    public DataCommunicationEncyclopedia(RestTemplate restTemplate) {
        toolInfo.setName("query_data_communication_encyclopedia")
                .setDescription("查询数据通信领域的名词，返回其介绍。任何数据通信领域的名词都必须先尝试调用这个工具尝试查询。")
                .setParameters(
                        Collections.singletonList(
                                new FunctionCallingModel.Tool.Parameter()
                                        .setName("word")
                                        .setType("string")
                                        .setDescription("数据通信领域的名词")
                                        .setRequired(true)
                        )
                );
        this.restTemplate = restTemplate;
    }

    @Override
    public List<String> apply(List<Map<String, Object>> requests) {
        List<String> results = new ArrayList<>();
        for (Map<String, Object> request : requests) {
            results.add(viewIpBaikeContent(url + request.getOrDefault("word", "").toString()));
        }
        return results;
    }

    private String viewIpBaikeContent(String url) {
        WebResult webResult = new WebResult();
        try {
            // 设置请求头伪装成浏览器
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3");
            HttpEntity<String> entity = new HttpEntity<>(headers);
            // 发送请求并获取返回数据
            ResponseEntity<String> responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            // 检查是否重定向
            if (responseEntity.getStatusCode().is3xxRedirection()) {
                String redirectUrl = responseEntity.getHeaders().getLocation().toString();
                responseEntity = restTemplate.exchange(redirectUrl, HttpMethod.GET, entity, String.class);
            }
            String response = responseEntity.getBody();
            // 使用Jsoup解析网页内容
            org.jsoup.nodes.Document doc = Jsoup.parse(response);
            webResult.title = doc.title();
            if ("检索失败页面".equals(webResult.title)) {
                return "未找到相关条目";
            }
            // 截取“IP知识百科 > ”之后和“相关词条”之前的内容
            String rawText = doc.body().text();
            // 定义开始和结束标记
            String startMarker = "IP知识百科 > ";
            String endMarker = "相关词条";
            // 截取内容
            int startIndex = rawText.indexOf(startMarker);
            int endIndex = rawText.indexOf(endMarker);
            if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
                webResult.content = rawText.substring(startIndex + startMarker.length(), endIndex).trim();
            } else {
                // 可选：记录日志或设置默认值
                webResult.content = rawText;
            }
            return webResult.content;
        } catch (Exception e) {
            return null;
        }
    }

    @lombok.Data
    class WebResult {
        String title;
        String content;
    }
}
