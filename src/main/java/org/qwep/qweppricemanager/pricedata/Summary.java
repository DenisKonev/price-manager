package org.qwep.qweppricemanager.pricedata;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class Summary {
    private Integer loadedDataRowsCount;
    private Integer declinedDataRowsCount;

    public Summary() {
        this.loadedDataRowsCount = 0;
        this.declinedDataRowsCount = 0;
    }

    public void incrementLoadedDataRowsCount() {
        loadedDataRowsCount++;
    }

    public void incrementDeclinedDataRowsCount() {
        loadedDataRowsCount++;
    }
}
