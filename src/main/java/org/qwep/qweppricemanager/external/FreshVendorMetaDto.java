package org.qwep.qweppricemanager.external;

import lombok.*;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class FreshVendorMetaDto {

    private String accountId;
    private String adminCode;
    private String viewCode;
    private UUID vendorId;
}
