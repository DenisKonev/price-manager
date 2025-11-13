package org.qwep.qweppricemanager.pricesender;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Add1CPriceDTO {
    private String vendorName;
    private String token;
    private String vendorSite;
    private String vendorMail;
    private String currency;
}
