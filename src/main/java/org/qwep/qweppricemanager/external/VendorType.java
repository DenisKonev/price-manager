package org.qwep.qweppricemanager.external;

public enum VendorType {
    ODIN_C("1C"),
    USERAPI("API");

    public final String header;

    VendorType(String header) {
        this.header = header;
    }
}
