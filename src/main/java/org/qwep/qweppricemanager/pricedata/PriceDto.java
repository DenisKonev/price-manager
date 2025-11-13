package org.qwep.qweppricemanager.pricedata;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.Validate;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class PriceDto {

    private Long id;
    private String[] tableRef;
    private String brand;
    private String article;
    private String partname;
    private String quantity;
    private String multiplicity;
    private String delivery;
    private String status;
    private String warehouse;
    private String price;
    private String notes;
    private String photo;
    private String currency;

    @JsonIgnore
    public boolean isValid() throws IllegalArgumentException {
        try {
            Validate.notBlank(price);
            Validate.notBlank(article);
            Validate.notBlank(brand);
            Validate.notBlank(quantity);
            Validate.notBlank(partname);
            Validate.matchesPattern(quantity, "^(?=.*\\d).+$");
            Validate.matchesPattern(price, "^(?=.*\\d).+$");
            return true;
        } catch (AssertionError | NullPointerException | IllegalArgumentException ignored) {
            return false;
        }
    }

    @JsonIgnore
    public Double parseAndRoundPrice() throws IllegalArgumentException {
        String normalizedPrice = getPrice().replaceAll("[^\\d,.]", "");
        normalizedPrice = normalizedPrice.replace(",", ".");
        int lastDotIndex = normalizedPrice.lastIndexOf('.');

        if (lastDotIndex != -1) {
            normalizedPrice = normalizedPrice.substring(0, lastDotIndex).replace(".", "")
                    + normalizedPrice.substring(lastDotIndex);
        }

        if (normalizedPrice.startsWith(".") || normalizedPrice.endsWith(".")) {
            throw new IllegalArgumentException("Некорректный формат цены: " + price);
        }

        BigDecimal parsedPrice = new BigDecimal(normalizedPrice);
        parsedPrice = parsedPrice.setScale(2, RoundingMode.HALF_UP);

        return parsedPrice.doubleValue();
    }
}
