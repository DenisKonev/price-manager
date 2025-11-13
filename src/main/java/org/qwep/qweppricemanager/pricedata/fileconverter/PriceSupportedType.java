package org.qwep.qweppricemanager.pricedata.fileconverter;

public enum PriceSupportedType {

    XLS(".xls"),
    XLSX(".xlsx"),
    CSV(".csv");


    public final String fmt;

    PriceSupportedType(String fmt) {
        this.fmt = fmt;
    }
}

