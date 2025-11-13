package org.qwep.qweppricemanager.search;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.qwep.qweppricemanager.pricedata.PriceDto;
import org.qwep.qweppricemanager.pricesender.NoPriceTableRefsException;
import org.qwep.qweppricemanager.pricesender.PriceSenderInfoEntity;
import org.qwep.qweppricemanager.pricesender.PriceSenderService;
import org.qwep.qweppricemanager.pricesender.enums.PriceState;
import org.qwep.qweppricemanager.rest.dto.BrandArticleDto;
import org.qwep.qweppricemanager.search.exception.*;
import org.springframework.dao.DataAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class SearchService {
    private final ObjectMapper mapper;
    private final DataSource dataSource;
    private final PriceSenderService priceSenderService;
    private final SearchRepository searchRepository;

    @Retryable(retryFor = InProgressStateException.class, maxAttempts = 2, backoff = @Backoff(delay = 3000))
    public List<PriceDto> getPrices(List<BrandArticleDto> crosses, Optional<UUID> vendorId, Boolean isCrosses) {

        PriceSenderInfoEntity psie = priceSenderService.getPriceSenderInfoEntity(vendorId.orElseThrow())
                .orElseThrow(() -> new NoSuchVendorException("Vendor doesn't exist, id: " + vendorId.orElseThrow()));
        try {

            priceSenderService.checkPriceInfoState(psie);

            UUID priceTableRef = priceSenderService.getUUIDPriceTableRef(psie);

            List<PriceDto> priceDtoList = new ArrayList<>();
            crosses
                    .parallelStream()
                    .forEach(c -> priceDtoList.addAll(findItems(priceTableRef,
                            c.getBrand(), c.getArticle(), isCrosses)));

            if (priceDtoList.isEmpty()) {
                crosses
                        .parallelStream()
                        .forEach(c -> priceDtoList.addAll(findItems(priceTableRef,
                                c.getArticle(), isCrosses)));
            }

            return priceDtoList;

        } catch (AlreadyErrorStateException | AwaitClassificationStateException e) {
            log.debug("Get prices in SearchService.getPrice failed. VendorId: {} Exception: {}; Requested crosses: {}",
                    vendorId.orElse(null), e.getMessage(), crosses);
            return Collections.emptyList();
        } catch (InProgressStateException e) {
            throw e;
        } catch (EmptyCurrentStateException e) {
            psie.setCurrentState(PriceState.ERROR_EMPTY_STATE.state);
            priceSenderService.savePriceSenderInfoEntity(psie);

            return logAndGetEmptyList(crosses, vendorId, e);
        } catch (NoPriceTableRefsException e) {
            psie.setCurrentState(PriceState.ERROR_NO_REF.state);
            priceSenderService.savePriceSenderInfoEntity(psie);

            return logAndGetEmptyList(crosses, vendorId, e);
        } catch (NoSuchPriceTable exception) {
            if (psie.getCurrentState().equals("in_progress")) throw exception;

            psie.setCurrentState(PriceState.ERROR_NO_TABLE.state);
            psie.setPriceTableRefs(new String[]{});
            priceSenderService.savePriceSenderInfoEntity(psie);

            return logAndGetEmptyList(crosses, vendorId, exception);
        } catch (Exception e) {
            return logAndGetEmptyList(crosses, vendorId, e);
        }
    }

    private List<PriceDto> findItems(UUID table, String brand, String article, Boolean isCrosses) {
        article = removeInvalidCharacters(article);
        brand = removeInvalidCharacters(brand);

        try {
            return searchRepository.findItemsByBrandAndArticle(table.toString(), article, brand, isCrosses);
        } catch (DataAccessException e) {
            if (e.getCause().toString().contains("ERROR: relation"))
                throw new NoSuchPriceTable(String.format("Table %s doesn't exist, msg: %s, cause: %s", table, e.getMessage(), e.getCause()));
            throw e;
        }
    }

    private List<PriceDto> findItems(UUID table, String article, Boolean isCrosses) {
        article = removeInvalidCharacters(article);
        try {
            return searchRepository.findItemsByArticle(table.toString(), article, isCrosses);
        } catch (DataAccessException e) {
            if (e.getCause().toString().contains("ERROR: relation"))
                throw new NoSuchPriceTable(String.format("Table %s doesn't exist, msg: %s, cause: %s", table, e.getMessage(), e.getCause()));
            throw e;
        }
    }

    private String removeInvalidCharacters(String string) {
        return string.replaceAll("'", "");
    }

    private List<PriceDto> logAndGetEmptyList(List<BrandArticleDto> crosses, Optional<UUID> vendorId, Exception e) {
        List<String> formattedCrosses = crosses.stream()
                .map(brandArticleDto -> "art: " + brandArticleDto.getArticle() + " brand: " + brandArticleDto.getBrand()).toList();
        log.error("Get prices in SearchService.getPrice failed. VendorId: {} Exception: {}; Requested crosses: {}",
                vendorId.orElse(null), e.getMessage(), formattedCrosses);
        return Collections.emptyList();
    }

    @Transactional
    public List<PriceDto> fetchPriceItems(Long itemId, String refTable) {
        List<PriceDto> items = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(String.format("select * from get_price_item(%d,'%s');", itemId, refTable))) {
            ResultSet rs = stmt.executeQuery();
            rs.next();
            String resultJson = rs.getString(1);
            rs.close();
            log.debug("got crosses price result: {}", resultJson);
            items = mapper.readValue(resultJson, new TypeReference<>() {
            });
        } catch (SQLException e) {
            log.error("error while request stored price data func with cause: {}", e.getLocalizedMessage());
        } catch (JsonMappingException e) {
            log.error("error while json mapping price data with cause: {}", e.getLocalizedMessage());
        } catch (JsonProcessingException e) {
            log.error("error while json processing price data with cause: {}", e.getLocalizedMessage());
        }
        return items;
    }
}
