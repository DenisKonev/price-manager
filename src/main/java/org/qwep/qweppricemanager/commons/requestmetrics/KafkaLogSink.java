package org.qwep.qweppricemanager.commons.requestmetrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.zalando.logbook.*;
import java.io.IOException;

@Slf4j
public class KafkaLogSink implements Sink {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaLogSink(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void write(Correlation correlation, HttpRequest request, HttpResponse response) throws IOException {
        int port = request.getPort().isPresent() ? request.getPort().get() : 0;
        LogMessage logMessage = new LogMessage(
                request.getMethod(),
                request.getRequestUri(),
                request.getHeaders().toString(),
                request.getBodyAsString().length() > 5000
                        ? reduceBody(request.getBodyAsString())
                        : request.getBodyAsString(),
                response.getStatus(),
                response.getHeaders().toString(),
                response.getBodyAsString().length() > 5000
                        ? reduceBody(response.getBodyAsString())
                        : response.getBodyAsString(),
                request.getHost(),
                port,
                System.currentTimeMillis(),
                correlation.getDuration().toMillis()
        );
        Message<LogMessage, LogMessageSchema> message = new Message<>(
                LogMessageSchema.instance,
                logMessage
        );
        kafkaTemplate.send("price_manager_http_activity",
                objectMapper.writeValueAsString(message));
    }

    private String reduceBody(String body) {
        return body.substring(0, Math.min(body.length(), 5000));
    }

    @Override
    public void write(Precorrelation precorrelation, HttpRequest request) {

    }

}

