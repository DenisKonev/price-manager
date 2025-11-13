package org.qwep.qweppricemanager.data.config;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;


public class HttpHeadersFactory {

    private HttpHeadersFactory() {
    }

    public static HttpHeaders createDataapiRequesHeaders() {

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.ALL));
        headers.setAcceptCharset(List.of(StandardCharsets.UTF_8));
        MediaType mediaType = new MediaType("application", "json", StandardCharsets.UTF_8);
        headers.setContentType(mediaType);
        headers.set("Authorization", "Bearer 8277CD79-C119-4615-B0EF-FA1DE048AB99");

        return headers;

    }

}
