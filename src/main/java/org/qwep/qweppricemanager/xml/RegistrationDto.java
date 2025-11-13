package org.qwep.qweppricemanager.xml;

import io.swagger.v3.oas.annotations.media.Schema;


public record RegistrationDto(
        @Schema(defaultValue = "https://zhilichev.ru/downloads/interauto/farpost/pricelist.xml") String link,
                              @Schema(defaultValue = "[0,1]") String offsting,
        @Schema(defaultValue = "s.panin@qwep.ru") String vendorEmail,
                              @Schema(defaultValue = "RUB") String currency,
                              @Schema(defaultValue = "MockTest_e960a2fc1b3eab366d08ee38b1bad0cfbf1ec3ae") String vendorName) {


}
