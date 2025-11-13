package org.qwep.qweppricemanager.external;

import lombok.extern.slf4j.Slf4j;
import org.qwep.qweppricemanager.data.config.HttpHeadersFactory;
import org.qwep.qweppricemanager.rest.dto.BrandArticleDto;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
@Slf4j
public class DataApiService {
    private final RestTemplate restTemplate;

    public DataApiService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public List<BrandArticleDto> getCrossesBy(String brand, String article) throws RestClientException {
        HttpHeaders headers = HttpHeadersFactory.createDataapiRequesHeaders();
        HttpEntity<BrandArticleDto> requestHttpEntity = new HttpEntity<>(new BrandArticleDto(brand, article), headers);
        String url = "https://dataapi.kube.qwep.ru/getcrossitems";
        ResponseEntity<BrandArticleDto[]> crossesResponse =
                restTemplate.exchange(url, HttpMethod.POST, requestHttpEntity, BrandArticleDto[].class);
        log.debug("got response: {}", crossesResponse);
        return List.of(crossesResponse.getBody() != null ? crossesResponse.getBody() : new BrandArticleDto[]{});
    }

}
