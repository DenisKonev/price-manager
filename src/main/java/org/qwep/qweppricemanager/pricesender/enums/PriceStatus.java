package org.qwep.qweppricemanager.pricesender.enums;

public enum PriceStatus {

    EMPTY("empty"),
    UNCLASSIFIED("unclassified"),
    PUBLISHED("published"),
    OLD("old");

    public final String status;

    PriceStatus(String status) {
        this.status = status;
    }
}
