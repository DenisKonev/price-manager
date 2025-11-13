package org.qwep.qweppricemanager.pricesender;

import lombok.Data;

import java.util.UUID;

@Data
public class PriceTableRef {

    private UUID priceTableRef;

    private PriceTableRef(String[] priceTableRef) {
        this.priceTableRef = UUID.fromString(priceTableRef[0]);
    }

    public static PriceTableRef of(UUID priceTableRef) {
        return new PriceTableRef(new String[]{priceTableRef.toString()});
    }

    public static PriceTableRef of(String[] priceTableRef) {
        return new PriceTableRef(priceTableRef);
    }
}
