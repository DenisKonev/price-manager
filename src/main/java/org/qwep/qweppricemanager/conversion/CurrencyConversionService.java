package org.qwep.qweppricemanager.conversion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.money.CurrencyUnit;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
public class CurrencyConversionService {

    private final RestTemplate restTemplate = new RestTemplate();
    private String ER_LINK = "http://192.168.0.41:30548/get-rate?date=%s&code=%s";

    public String convert(String price, CurrencyUnit newCurrency, CurrencyUnit oldCurrency) throws NullPointerException {
        if (newCurrency.equals(oldCurrency)) {
            return price;
        }
        double oldPrice = Double.parseDouble(price.replaceAll("[^0-9,.]||[.]$", "").replace(",", "."));
        double newPrice = oldPrice *
                Double.parseDouble(Objects.requireNonNull(getConversionDto(oldCurrency
                        .getCurrencyCode())).getCurrency().replace(",", ".")) /
                Double.parseDouble(Objects.requireNonNull(getConversionDto(newCurrency
                        .getCurrencyCode())).getCurrency().replace(",", "."));
        return String.format("%.2f", newPrice);
    }

    private String buildLink(String currency) {
        return String.format(
                ER_LINK,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), currency);
    }

    private ConversionDto getConversionDto(String currency) {
        return restTemplate.getForObject(buildLink(currency), ConversionDto.class);
    }
}
