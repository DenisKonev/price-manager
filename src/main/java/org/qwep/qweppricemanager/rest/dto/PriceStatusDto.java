package org.qwep.qweppricemanager.rest.dto;

import lombok.*;
import org.qwep.qweppricemanager.pricesender.enums.PriceStatus;

@Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PriceStatusDto {

    private String lastUpdated;
    private PriceStatus status;
    private Integer dataRowsCount;

}
