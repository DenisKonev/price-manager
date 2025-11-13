package org.qwep.qweppricemanager.commons.requestmetrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.zalando.logbook.Logbook;

@Configuration
public class LogbookConf {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public LogbookConf(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Bean
    public Logbook logbook() {
        return Logbook.builder()
                .sink(new KafkaLogSink(kafkaTemplate, objectMapper))
                .build();
    }
}
