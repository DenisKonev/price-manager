package org.qwep.qweppricemanager.external;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.qwep.qweppricemanager.pricesender.PriceSenderInfoEntity;
import org.qwep.qweppricemanager.pricesender.enums.PriceSecurityType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@EnableRetry
public class ApiQwepService {
    private final ApiQwepQueriesConfig apiQwepQueriesConfig;
    private final Connection apiQwep;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final DbConfig dbConfig;

    @Autowired
    public ApiQwepService(
            ApiQwepQueriesConfig apiQwepQueriesConfig,
            @Qualifier("apiQwep") Connection apiQwep,
            RestTemplate restTemplate, DbConfig dbConfig) {
        this.apiQwepQueriesConfig = apiQwepQueriesConfig;
        this.apiQwep = apiQwep;
        this.restTemplate = restTemplate;
        this.dbConfig = dbConfig;
        this.objectMapper = new ObjectMapper();
    }

//    public Optional<String> getVendorIdBySubjectPriceCode(String priceCode) {
//        String query = apiQwepQueriesConfig.getVendorIdByPriceCodeQuery();
//        Optional<String> vendorId = execQuery(query, priceCode, String.class);
//        vendorId.ifPresent(vId -> log.info("found vendorId '{}' by price code '{}'", vendorId.get(), priceCode));
//        return vendorId;
//    }

    //@LogExecTimeAspect
    public Optional<String> getVendorIdByEmail(String email) {
        String query = apiQwepQueriesConfig.getVendorIdByEmailQuery();
        Optional<String> vendorId = execQuery(query, email, String.class);
        vendorId.ifPresent(vId -> log.info("found vendorId '{}' by email '{}'", vendorId.get(), email));
        return vendorId;
    }


    public FreshVendorMetaDto createVendorUserapi(String accessToken, String vendorName) throws Exception {
        Validate.notBlank(vendorName);
        Validate.notBlank(accessToken);
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("X-Type", "API");
        HttpEntity<CreateUserApiVendorDTO> requestHttpEntity =
                new HttpEntity<>(new CreateUserApiVendorDTO(accessToken, vendorName), httpHeaders);
        String url = "https://api.qwep.ru/addPriceListVendor";
        ResponseEntity<String> freshVendorResponse =
                restTemplate.exchange(url, HttpMethod.POST, requestHttpEntity, String.class);
        log.debug("got response: {}", freshVendorResponse);
        try {
            return freshVendorResponse.getBody() != null
                    ? objectMapper.readValue(freshVendorResponse.getBody(), FreshVendorMetaDto.class)
                    : new FreshVendorMetaDto();
        } catch (JsonProcessingException exception) {
            log.error("failed to create new vendor because: {}", exception.getLocalizedMessage());
            throw new Exception("Can't create new vendor", exception);
        }
    }


    public FreshVendorMetaDto createVendor1C(Create1CVendorDTO create1CVendorDTO) throws Exception {
        Validate.notBlank(create1CVendorDTO.getVendorName());
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("X-Type", "1C");
        HttpEntity<Create1CVendorDTO> requestHttpEntity =
                new HttpEntity<>(create1CVendorDTO, httpHeaders);
        String url = "https://api.qwep.ru/addPriceListVendor";
        ResponseEntity<String> freshVendorResponse =
                restTemplate.exchange(url, HttpMethod.POST, requestHttpEntity, String.class);
        log.debug("got response: {}", freshVendorResponse);
        try {
            return freshVendorResponse.getBody() != null
                    ? objectMapper.readValue(freshVendorResponse.getBody(), FreshVendorMetaDto.class)
                    : new FreshVendorMetaDto();
        } catch (JsonProcessingException exception) {
            log.error("failed to create new vendor because: {}", exception.getLocalizedMessage());
            throw new Exception("Can't create new vendor", exception);
        }
    }


    public <T> Optional<T> execQuery(String query, Object param, Class<T> returnTypeClass) {
        try (Connection connection = DriverManager.getConnection(dbConfig.getApiQwepDbUrl());
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setObject(1, param);
            ResultSet rs = stmt.executeQuery();
            rs.next();

            if (returnTypeClass.equals(String.class)) {
                Optional<T> resultOpt = (Optional<T>) Optional.of(rs.getString(1));
                rs.close();
                return resultOpt;
            } else {
                rs.close();
                log.warn("executed method of Core has no support of type {}", returnTypeClass);
                return Optional.empty();
            }
        } catch (SQLException sqlex) {
            log.debug("failed to execute query because: '{}'", sqlex.getLocalizedMessage());
            return Optional.empty();
        }
    }

    @Retryable(maxAttempts = 6, backoff = @Backoff(delay = 5000L))
    public String getQuserTokenByUaAccessToken(String accessToken) {
        return restTemplate.getForObject("https://api.qwep.ru/getQUserTokenByUAToken/" + accessToken, String.class);
    }

    public Optional<String> getPromoEmailFromApiQwep(UUID vendorId) {
        return execQuery("select email from vendor v where v.id = ?", vendorId, String.class);
    }

    public PriceSecurityType getPriceSecurityStatus(PriceSenderInfoEntity psi) {
        boolean enabled;
        try (Connection connection = DriverManager.getConnection(dbConfig.getApiQwepDbUrl());
             PreparedStatement stmt = connection.prepareStatement(apiQwepQueriesConfig.getVendorSecurityType())) {
            stmt.setObject(1, UUID.fromString(psi.getVendorId()));
            ResultSet rs = stmt.executeQuery();
            rs.next();
            enabled = rs.getBoolean(1);
            rs.close();
        } catch (SQLException e) {
            log.error("error while request price security type because: {}", e.getLocalizedMessage());
            enabled = false;
        }
        return enabled ? PriceSecurityType.PUBLIC : PriceSecurityType.PRIVATE;
    }

//    public Optional<String> getVendorNameById(Optional<String> vendorId) {
//        if (vendorId.isPresent()) {
//            try (Connection connection = DriverManager.getConnection(dbConfig.getApiQwepDbUrl());
//                 PreparedStatement stmt = connection.prepareStatement(apiQwepQueriesConfig.getVendorCoreName())) {
//                stmt.setObject(1, UUID.fromString(vendorId.get()));
//                ResultSet rs = stmt.executeQuery();
//                rs.next();
//                Optional<String> result = Optional.ofNullable(rs.getString(1));
//                rs.close();
//                return result;
//
//            } catch (SQLException e) {
//                log.error("error while request vendor core name with cause: {}", e.getLocalizedMessage());
//                return Optional.empty();
//            }
//        } else {
//            return Optional.empty();
//        }
//    }

    public synchronized List<String> getVendorIdByQUserToken(String quserToken) {
        List<String> vendorIds = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(dbConfig.getApiQwepDbUrl());
             PreparedStatement stmt = connection.prepareStatement(apiQwepQueriesConfig.getVendorIdsByQuserToken())) {
            stmt.setString(1, quserToken);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                vendorIds.add(rs.getString(1));
            }
            rs.close();
        } catch (SQLException sqlex) {
            log.error("failed to get vendorIds by quser token: '{}' because: '{}'",
                    quserToken, sqlex.getLocalizedMessage());
            vendorIds = List.of();
        }
        return vendorIds;
    }
}
