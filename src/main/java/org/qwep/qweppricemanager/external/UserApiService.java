package org.qwep.qweppricemanager.external;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.qwep.qweppricemanager.external.userapidto.*;
import org.qwep.qweppricemanager.pricesender.PriceSenderInfoEntity;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
@Slf4j
public class UserApiService {
    private final RestTemplate restTemplate;

    public UserApiService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public FreshVendorMetaDto addFreshVendorToUserapiAccount(String accessToken, FreshVendorMetaDto fvmd) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);

        UserapiAccount uac = new UserapiAccount();
        uac.setVid(fvmd.getVendorId().toString());
        uac.setLogin("qwep");
        uac.setPassword("qwep");
        UserapiAccountsAddRequestBody uaarb = new UserapiAccountsAddRequestBody();
        uaarb.setRequest(new UserapiRequest(new UserapiRequestData(List.of(uac))));

        HttpEntity<UserapiAccountsAddRequestBody> requestHttpEntity = new HttpEntity<>(uaarb, headers);
        String url = "https://userapi.qwep.ru/accounts/add";
        try {
            ResponseEntity<JsonNode> response =
                    restTemplate.exchange(url, HttpMethod.POST, requestHttpEntity, JsonNode.class);
            log.info("fresh vendor '{}' has been added to userapi account maybe...", fvmd.getVendorId());
            fvmd.setAccountId(response.getBody().get("Response").get("entity").get("accounts").get(0).get("id").asText());
            return fvmd;
        } catch (Exception e) {
            log.error("failed to add fresh vendor to userapi account because: {}", e.getLocalizedMessage());
            return fvmd;
        }
    }

    public void disableVendorUserapiAccount(PriceSenderInfoEntity psi, String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);

        UserapiAccountToDisable uad = new UserapiAccountToDisable(psi.getUserApiAccountId());
        UserapiAccountsToDisableRequestBody uaadr = new UserapiAccountsToDisableRequestBody();
        uaadr.setRequest(new UserapiAccountDisableRequest(new UserapiAccountDisableRequestData(List.of(uad))));

        HttpEntity<UserapiAccountsToDisableRequestBody> requestHttpEntity = new HttpEntity<>(uaadr, headers);
        String url = "https://userapi.qwep.ru/accounts/delete";
        try {
            restTemplate.exchange(url, HttpMethod.POST, requestHttpEntity, JsonNode.class);
            log.info("web vendor '{}' has been disabled", psi.getVendorId());
        } catch (Exception e) {
            log.error("failed to add fresh vendor to userapi account because: {}", e.getLocalizedMessage());
        }
    }

    public void disableVendorUserapiAccount(String accountId, String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);

        UserapiAccountToDisable uad = new UserapiAccountToDisable(accountId);
        UserapiAccountsToDisableRequestBody uaadr = new UserapiAccountsToDisableRequestBody();
        uaadr.setRequest(new UserapiAccountDisableRequest(new UserapiAccountDisableRequestData(List.of(uad))));

        HttpEntity<UserapiAccountsToDisableRequestBody> requestHttpEntity = new HttpEntity<>(uaadr, headers);
        String url = "https://userapi.qwep.ru/accounts/delete";
        try {
            restTemplate.exchange(url, HttpMethod.POST, requestHttpEntity, JsonNode.class);
            log.info("web vendor '{}' has been disabled", accessToken);
        } catch (Exception e) {
            log.error("failed to add fresh vendor to userapi account because: {}", e.getLocalizedMessage());
        }
    }
}
