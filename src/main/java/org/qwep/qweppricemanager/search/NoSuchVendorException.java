package org.qwep.qweppricemanager.search;

import java.util.NoSuchElementException;

public class NoSuchVendorException extends NoSuchElementException {
    public NoSuchVendorException(String message) {
        super(message);
    }
}
