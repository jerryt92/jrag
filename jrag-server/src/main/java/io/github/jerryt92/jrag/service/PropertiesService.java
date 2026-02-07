package io.github.jerryt92.jrag.service;

import io.github.jerryt92.jrag.mapper.mgb.PropertiesPoMapper;
import io.github.jerryt92.jrag.model.PropertyDto;
import io.github.jerryt92.jrag.po.mgb.PropertiesPo;
import io.github.jerryt92.jrag.po.mgb.PropertiesPoExample;
import io.github.jerryt92.jrag.event.PropertiesUpdatedEvent;
import jakarta.annotation.PostConstruct;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PropertiesService {
    public static final String RETRIEVE_TOP_K = "RETRIEVE_TOP_K";
    public static final String RETRIEVE_METRIC_TYPE = "RETRIEVE_METRIC_TYPE";
    // score比较表达式
    public static final String RETRIEVE_METRIC_SCORE_COMPARE_EXPR = "RETRIEVE_METRIC_SCORE_COMPARE_EXPR";
    public static final String RETRIEVE_DENSE_WEIGHT = "RETRIEVE_DENSE_WEIGHT";
    public static final String RETRIEVE_SPARSE_WEIGHT = "RETRIEVE_SPARSE_WEIGHT";

    private final Map<String, String> properties = new HashMap<>();
    private final PropertiesPoMapper propertiesPoMapper;
    private final SqlSessionFactory sqlSessionFactory;
    private final ApplicationEventPublisher eventPublisher;

    public PropertiesService(PropertiesPoMapper propertiesPoMapper,
                             SqlSessionFactory sqlSessionFactory,
                             ApplicationEventPublisher eventPublisher) {
        this.propertiesPoMapper = propertiesPoMapper;
        this.sqlSessionFactory = sqlSessionFactory;
        this.eventPublisher = eventPublisher;
    }

    @PostConstruct
    public void init() {
        List<PropertiesPo> propertiesPos = propertiesPoMapper.selectByExample(new PropertiesPoExample());
        for (PropertiesPo propertiesPo : propertiesPos) {
            properties.put(propertiesPo.getPropertyName(), propertiesPo.getPropertyValue());
        }
    }

    public String getProperty(String propertyName) {
        String propertyValue = properties.get(propertyName);
        if (propertyValue == null) {
            PropertiesPo propertiesPo = propertiesPoMapper.selectByPrimaryKey(propertyName);
            if (propertiesPo != null) {
                propertyValue = propertiesPo.getPropertyValue();
                properties.put(propertyName, propertyValue);
            }
        }
        return propertyValue;
    }

    public Map<String, String> getProperties(Collection<String> propertyNames) {
        Map<String, String> result = new HashMap<>();
        if (CollectionUtils.isEmpty(propertyNames)) {
            return result;
        }
        for (String propertyName : propertyNames) {
            String propertyValue = getProperty(propertyName);
            result.put(propertyName, propertyValue);
        }
        return result;
    }

    public void putProperty(List<PropertyDto> propertyDtoList) {
        try (SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH)) {
            PropertiesPoMapper batchPropertiesPoMapper = sqlSession.getMapper(PropertiesPoMapper.class);
            for (PropertyDto propertyDto : propertyDtoList) {
                PropertiesPo propertiesPo = new PropertiesPo();
                propertiesPo.setPropertyName(propertyDto.getPropertyName());
                propertiesPo.setPropertyValue(propertyDto.getPropertyValue());
                int updated = batchPropertiesPoMapper.updateByPrimaryKeySelective(propertiesPo);
                if (updated == 0) {
                    batchPropertiesPoMapper.insertSelective(propertiesPo);
                }
                properties.put(propertyDto.getPropertyName(), propertyDto.getPropertyValue());
            }
            sqlSession.commit();
        }
        if (!CollectionUtils.isEmpty(propertyDtoList)) {
            List<String> propertyNames = propertyDtoList.stream()
                    .map(PropertyDto::getPropertyName)
                    .toList();
            eventPublisher.publishEvent(new PropertiesUpdatedEvent(propertyNames));
        }
    }
}
