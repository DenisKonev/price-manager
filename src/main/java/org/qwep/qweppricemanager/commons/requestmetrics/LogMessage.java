package org.qwep.qweppricemanager.commons.requestmetrics;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class LogMessage {
    private String httpMethod;
    private String requestURI;
    private String requestHeaders;
    private String requestBody;
    private Integer responseStatus;
    private String responseHeaders;
    private String responseBody;
    private String host;
    private Integer port;
    private Long date;
    private Long timeExecution;
}

