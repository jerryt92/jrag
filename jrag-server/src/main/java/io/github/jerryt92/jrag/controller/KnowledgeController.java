package io.github.jerryt92.jrag.controller;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import io.github.jerryt92.jrag.config.annotation.RequiredRole;
import io.github.jerryt92.jrag.model.ErrorResponseDto;
import io.github.jerryt92.jrag.model.KnowledgeAddDto;
import io.github.jerryt92.jrag.model.KnowledgeGetListDto;
import io.github.jerryt92.jrag.model.KnowledgeRetrieveItemDto;
import io.github.jerryt92.jrag.model.KnowledgeRetrieveResponseDto;
import io.github.jerryt92.jrag.model.security.SessionBo;
import io.github.jerryt92.jrag.server.api.KnowledgeApi;
import io.github.jerryt92.jrag.service.rag.knowledge.KnowledgeService;
import io.github.jerryt92.jrag.service.rag.retrieval.Retriever;
import io.github.jerryt92.jrag.service.security.LoginService;
import io.github.jerryt92.jrag.utils.HashUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Slf4j
@RestController
@RequiredRole(1)
public class KnowledgeController implements KnowledgeApi {
    private final KnowledgeService knowledgeService;
    private final Retriever retriever;
    private final LoginService loginService;

    public KnowledgeController(KnowledgeService knowledgeService, Retriever retriever, LoginService loginService) {
        this.knowledgeService = knowledgeService;
        this.retriever = retriever;
        this.loginService = loginService;
    }

    @Override
    public ResponseEntity<KnowledgeGetListDto> getKnowledge(Integer offset, Integer limit, String search) {
        return ResponseEntity.ok(knowledgeService.getKnowledge(offset, limit, search));
    }

    @Override
    public ResponseEntity<Void> putKnowledge(List<KnowledgeAddDto> knowledgeAddDto) {
        SessionBo session = loginService.getSession();
        knowledgeService.putKnowledge(knowledgeAddDto, session);
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> deleteKnowledge(List<String> requestBody) {
        SessionBo session = loginService.getSession();
        knowledgeService.deleteKnowledge(requestBody, session);
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<KnowledgeRetrieveResponseDto> retrieveKnowledge(String queryText, Integer topK) {
        List<KnowledgeRetrieveItemDto> knowledgeRetrieveItemDtos = retriever.retrieveKnowledge(queryText, topK);
        KnowledgeRetrieveResponseDto knowledgeRetrieveResponseDto = new KnowledgeRetrieveResponseDto();
        knowledgeRetrieveResponseDto.setData(knowledgeRetrieveItemDtos);
        return ResponseEntity.ok(knowledgeRetrieveResponseDto);
    }

    @Override
    public ResponseEntity<Resource> getJsonTemplate() {
        HttpHeaders headers = new HttpHeaders();
        String fileName = "json-template.json";
        headers.setContentDisposition(ContentDisposition.builder("attachment")
                .filename(URLEncoder.encode(fileName, StandardCharsets.UTF_8))
                .build());
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new ClassPathResource("static/json-template.json"));
    }

    @Override
    public ResponseEntity importKnowledgeByJson(MultipartFile jsonTemplate) {
        try {
            HashMap<String, String> outlineHashMap = new HashMap<>();
            HashMap<String, String> outlineHashHashMap = new HashMap<>();
            HashMap<String, String> textHashHashMap = new HashMap<>();
            // 读取/data/rag.json
            String text = new String(jsonTemplate.getBytes(), StandardCharsets.UTF_8);
            JSONArray ragJson = JSONArray.parseArray(text);
            List<KnowledgeAddDto> knowledgeAddDtoList = new ArrayList<>();
            List<JSONObject> jsonList = ragJson.toJavaList(JSONObject.class);
            for (int index = 0; index < jsonList.size(); index++) {
                JSONObject json = jsonList.get(index);
                KnowledgeAddDto knowledgeAddDto = new KnowledgeAddDto();
                List<String> outlines = json.getJSONArray("outline").toJavaList(String.class);
                for (String outlineItem : outlines) {
                    if (StringUtils.isBlank(outlineItem)) {
                        log.error("outline为空");
                        return ResponseEntity.badRequest().body(new ErrorResponseDto().message("outline为空"));
                    }
                    if (outlineHashMap.containsKey(outlineItem)) {
                        String errorMessage = "outline重复，outline : " + outlineItem;
                        log.error(errorMessage);
                        return ResponseEntity.badRequest().body(new ErrorResponseDto().message(errorMessage));
                    }
                    outlineHashMap.put(outlineItem, outlineItem);
                    String outlineHash = HashUtil.getMessageDigest(outlineItem.getBytes(StandardCharsets.UTF_8), HashUtil.MdAlgorithm.SHA1);
                    if (outlineHashHashMap.containsKey(outlineHash)) {
                        String errorMessage = "outline Hash重复, outline1：" + outlineHashHashMap.get(outlineHash) + ", outline2：" + outlineItem + ", outlineHash：" + outlineHash;
                        log.error(errorMessage);
                        return ResponseEntity.badRequest().body(new ErrorResponseDto().message(errorMessage));
                    }
                    outlineHashHashMap.put(outlineHash, outlineItem);
                }
                knowledgeAddDto.setOutline(outlines);
                String textHash;
                String textHashChunk;
                if (json.getString("text_chunk_value") != null && !json.getString("text_chunk_value").isEmpty()) {
                    textHash = HashUtil.getMessageDigest(json.getString("text_chunk_value").getBytes(StandardCharsets.UTF_8), HashUtil.MdAlgorithm.SHA1);
                    textHashChunk = json.getString("text_chunk_value");
                } else {
                    log.error("text_chunk_value为空");
                    return ResponseEntity.badRequest().body(new ErrorResponseDto().message("text_chunk_value为空"));
                }
                if (textHashHashMap.containsKey(textHash)) {
                    String errorMessage = "textHash重复, text1：" + textHashHashMap.get(textHash) + ", text2：" + textHashChunk + ", textHash：" + textHash;
                    log.error(errorMessage);
                    return ResponseEntity.badRequest().body(new ErrorResponseDto().message(errorMessage));
                }
                textHashHashMap.put(textHash, textHashChunk);
                knowledgeAddDto.setId(textHash);
                knowledgeAddDto.setTextChunk(textHashChunk);
                knowledgeAddDto.setDescription(json.getString("description"));
                knowledgeAddDto.setFileId(json.getInteger("file_id"));
                knowledgeAddDto.setClassify(json.getString("classify"));
                knowledgeAddDto.setFileLocation(json.getString("file_location"));
                knowledgeAddDtoList.add(knowledgeAddDto);
            }
            knowledgeService.putKnowledge(knowledgeAddDtoList, loginService.getSession());
        } catch (Throwable t) {
            log.error("", t);
        }
        return ResponseEntity.ok().build();
    }
}
