package org.qwep.qweppricemanager.commons.requestmetrics;

import lombok.Data;

import java.util.List;

@Data
public class LogMessageSchema {
    public static final LogMessageSchema instance = new LogMessageSchema();

    private String type;
    private List<Field> fields;

    private LogMessageSchema() {
        Field httpMethod = new Field("httpMethod", "string", true);
        Field requestURI = new Field("requestURI", "string", true);
        Field requestHeaders = new Field("requestHeaders", "string", true);
        Field requestBody = new Field("requestBody", "string", true);
        Field responseStatus = new Field("responseStatus", "int32", true);
        Field responseHeaders = new Field("responseHeaders", "string", true);
        Field responseBody = new Field("responseBody", "string", true);
        Field host = new Field("host", "string", true);
        Field port = new Field("port", "int32", true);
        Field date = new Field("date", "int64", true);
        Field timeExecution = new Field("timeExecution", "int32", true);
        this.type = "struct";
        this.fields = List.of(httpMethod, requestURI, requestHeaders, requestBody, responseStatus,
                responseHeaders, responseBody, host, port, date, timeExecution);
    }
}