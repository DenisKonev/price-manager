package org.qwep.qweppricemanager.pricesender;

import java.util.NoSuchElementException;

public class NoPriceTableRefsException extends NoSuchElementException {
    private PriceSenderInfoEntity psi;
    public NoPriceTableRefsException(String message){
        super(message);
    }
    public NoPriceTableRefsException(String message, Throwable cause){
        super(message, cause);
    }
    public NoPriceTableRefsException(String message, Throwable cause, PriceSenderInfoEntity psi){
        super(message, cause);
        this.psi = psi;
    }
}
