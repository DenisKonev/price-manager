package org.qwep.qweppricemanager.pricedata;

import org.qwep.qweppricemanager.pricesender.enums.PriceState;

public class PriceProcessingException extends Exception {
    public PriceProcessingException(String message) {
        super(message);
    }

    public PriceProcessingException(String message, Throwable throwable) {
        super(message, throwable);
    }

    public PriceState getErrorType() {
        return PriceState.ERROR;
    }
}
