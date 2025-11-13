package org.qwep.qweppricemanager.external;

import lombok.AllArgsConstructor;
import lombok.Getter;


@Getter
@AllArgsConstructor
public class CreateUserApiVendorDTO {
    private String userToken;
    private String vendorName;
}
