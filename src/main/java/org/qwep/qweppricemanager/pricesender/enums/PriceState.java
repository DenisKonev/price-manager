package org.qwep.qweppricemanager.pricesender.enums;

public enum PriceState {
    REGISTERED("registered"),
    AWAITS_CLASSIFICATION("awaits_classification"),
    IN_PROGRESS("in_progress"),
    CHANGED("changed"),
    UNCHANGED("unchanged"),
    ERROR("error"),
    ERROR_95("error_95"),
    ERROR_EMPTY("error_empty"),
    ERROR_NO_REF("error_no_ref"),
    ERROR_NO_TABLE("error_no_table"),
    ERROR_EMPTY_STATE("error_empty_state"),
    ERROR_SEARCH_EMPTY("error_search_empty");

    public final String state;

    PriceState(String state) {
        this.state = state;
    }
}
