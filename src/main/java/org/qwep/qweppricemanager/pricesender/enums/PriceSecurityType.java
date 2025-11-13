package org.qwep.qweppricemanager.pricesender.enums;

public enum PriceSecurityType {

    PUBLIC("public"),
    PRIVATE("private");

    public final String type;

    PriceSecurityType(String type) {
        this.type = type;
    }
}
