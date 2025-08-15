package io.github.jerryt92.jrag.service.llm.tools.impl;

import com.alibaba.fastjson2.JSONObject;
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
public class WebsiteViewer extends ToolInterface {
    private final RestTemplate restTemplate;

    public WebsiteViewer(RestTemplate restTemplate) {
        toolInfo.setName("view_website_content")
                .setDescription("访问传入的URL，返回解析出的网页内容")
                .setParameters(
                        Collections.singletonList(
                                new FunctionCallingModel.Tool.Parameter()
                                        .setName("url")
                                        .setType("string")
                                        .setDescription("website url")
                        )
                );
        this.restTemplate = restTemplate;
    }


    @Override
    public List<String> apply(List<Map<String, Object>> requests) {
        List<String> results = new ArrayList<>();
        for (Map<String, Object> request : requests) {
            results.add(viewWebsiteContent(request.getOrDefault("url", "").toString()));
        }
        return results;
    }

    private String viewWebsiteContent(String url) {
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
            webResult.content = doc.body().text();
            log.info("webResult : " + JSONObject.toJSONString(webResult));
            return JSONObject.toJSONString(webResult);
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
