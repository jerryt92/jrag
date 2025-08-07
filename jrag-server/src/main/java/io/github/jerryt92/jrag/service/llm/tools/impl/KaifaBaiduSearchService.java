package io.github.jerryt92.jrag.service.llm.tools.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import io.github.jerryt92.jrag.model.FunctionCallingModel;
import io.github.jerryt92.jrag.service.llm.tools.ToolInterface;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

@Slf4j
@Component
public class KaifaBaiduSearchService implements ToolInterface {
    @Autowired
    private RestTemplate restTemplate;
    private final ExecutorService concurrentQueryExecutor;

    public KaifaBaiduSearchService() {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setThreadNamePrefix("concurrentQueryExecutor");
        pool.setCorePoolSize(10);//核心线程数
        pool.setMaxPoolSize(10);//最大线程数
        pool.setKeepAliveSeconds(60 * 5);// 设置线程活跃时间（秒）
        pool.setQueueCapacity(50000);//线程队列
        pool.initialize();//线程初始化
        concurrentQueryExecutor = pool.getThreadPoolExecutor();
    }

    @Override
    public FunctionCallingModel.Tool getToolInfo() {
        return new FunctionCallingModel.Tool()
                .setName("search_code_questions_from_internet")
                .setDescription("调用搜索编程相关问题，返回最多5条结果条目。")
                .setParameters(
                        Collections.singletonList(
                                new FunctionCallingModel.Tool.Parameter()
                                        .setName("query")
                                        .setType("string")
                                        .setDescription("Search query")
                        )
                );
    }


    @Override
    public String apply(Map<String, Object> request) {
        String query = search(request.getOrDefault("query", "").toString());
        log.info("KaifaBaiduSearchService: " + query);
        return query;
    }

    private String search(String query) {
        // 构建请求URL
        String url = "https://kaifa.baidu.com/rest/v1/search";
        String queryParams = String.format("wd=%s&pageNum=1&pageSize=10",
                System.currentTimeMillis(), query);
        String fullUrl = url + "?" + queryParams;

        // 发送请求并获取返回数据
        String json = restTemplate.getForObject(fullUrl, String.class);
        Response response = JSONObject.parseObject(json, Response.class);
        if (response == null || response.data == null || response.data.documents == null || response.data.documents.data == null) {
            log.error("Failed to get valid response from Baidu search API.");
            return "Failed to get valid response from Baidu search API.";
        }
        List<Document> data = response.getData().getDocuments().getData();
        if (data.size() > 5) {
            data = data.subList(0, 5);
        }
        return "下面是从互联网上获取的信息摘要和url：" + JSONArray.toJSONString(data);
    }

    @lombok.Data
    class Response {
        String status;
        String message;
        Data data;
    }

    @lombok.Data
    class Data {
        Documents documents;
        String ecQuery;
        List<String> wordseg;
        String source;
        List<?> aggregations;
        Object operator;
        Object specialCount;
    }

    @lombok.Data
    class Documents {
        int pageNum;
        int pageSize;
        int totalCount;
        List<Document> data;
        int dataSize;
    }

    @lombok.Data
    class Document {
        TechDocDigest techDocDigest;
        Object strategyResponse;
    }

    @lombok.Data
    class TechDocDigest {
        String url;
        String title;
        String summary;
        Object readme;
        int upCount;
        String feedbackType;
        Object query;
        Object synonym;
        String weight;
        Object subLinks;
        Object subKeywords;
        String resourceType;
        String publishTime;
        String site;
        String fullSite;
        String similarId;
        String similarWeight;
        Object review;
        String realTitle;
        double score;
    }

    @lombok.Data
    class FinallyResult {
        String srcUrl;
        String title;
        String content;
    }
}
