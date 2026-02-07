package io.github.jerryt92.jrag.controller;

import com.alibaba.fastjson2.JSONObject;
import io.github.jerryt92.jrag.config.annotation.RequiredRole;
import io.github.jerryt92.jrag.constants.CommonConstants;
import io.github.jerryt92.jrag.model.QaTemplateItem;
import io.github.jerryt92.jrag.model.QaTemplateList;
import io.github.jerryt92.jrag.server.api.QaTemplateApi;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequiredRole(2)
public class QaTemplateController implements QaTemplateApi {
    final HttpServletRequest httpServletRequest;

    public QaTemplateController(HttpServletRequest httpServletRequest) {
        this.httpServletRequest = httpServletRequest;
    }

    @Override
    public ResponseEntity<QaTemplateList> getQaTemplate(Integer limit) {
        QaTemplateList qaTemplateList = new QaTemplateList();
        try (InputStream inputStream = QaTemplateController.class.getClassLoader().getResourceAsStream("qa-template.json")) {
            List<String> qaTemplates;
            if (inputStream != null) {
                JSONObject qaTemplateJson = JSONObject.parse(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
                qaTemplates = qaTemplateJson.getJSONArray(CommonConstants.ZH_CN).toList(String.class);
            } else {
                qaTemplates = Collections.emptyList();
            }
            int total = qaTemplates.size();
            if (limit > total) {
                // 如果 limit 超出范围，则默认返回全部
                limit = total;
            }
            // 打乱列表顺序
            Collections.shuffle(qaTemplates);
            // 截取前 limit 个元素
            List<String> selectedTemplates = qaTemplates.subList(0, limit);
            // 构建 QaTemplateItem 列表
            List<QaTemplateItem> data = selectedTemplates.stream()
                    .map(template -> new QaTemplateItem().question(template))
                    .collect(Collectors.toList());
            qaTemplateList.data(data);
            return ResponseEntity.ok(qaTemplateList);
        } catch (Throwable t) {
            log.error("", t);
            return ResponseEntity.ok(qaTemplateList);
        }
    }
}
