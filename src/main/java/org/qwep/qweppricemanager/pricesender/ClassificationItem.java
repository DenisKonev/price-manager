package org.qwep.qweppricemanager.pricesender;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ClassificationItem {

    @Schema(defaultValue = "store")
    private String priceHeader;
    @Schema(defaultValue = "BRAND")
    private String category;

    /**
     * trims priceHeader before return
     */
    public String getPriceHeader() {
        return priceHeader.trim();
    }

    public static ClassificationItem fromHeader(String header) {
        ClassificationItem cli = new ClassificationItem();
        cli.setPriceHeader(header);
        return cli;
    }
}
