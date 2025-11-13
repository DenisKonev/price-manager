package org.qwep.qweppricemanager.pricedata;

public enum PriceHeader {
    BRAND("brand"),
    ARTICLE("article"),
    PARTNAME("partname"),
    QUANTITY("quantity"),
    MULTIPLICITY("multiplicity"),
    DELIVERY("delivery"),
    STATUS("status"),
    WAREHOUSE("warehouse"),
    PRICE("price"),
    NOTES("notes"),
    PHOTO("photo"),
    CURRENCY("currency"),
    TRASH("trash");

    public final String cat;

    PriceHeader(String cat) {
        this.cat = cat;
    }
}
