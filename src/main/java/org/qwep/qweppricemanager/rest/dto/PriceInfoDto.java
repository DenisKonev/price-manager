package org.qwep.qweppricemanager.rest.dto;

import lombok.*;
import org.qwep.qweppricemanager.pricesender.PriceSenderInfoEntity;
import org.qwep.qweppricemanager.pricesender.enums.PriceSecurityType;
import org.qwep.qweppricemanager.pricesender.enums.PriceStatus;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class PriceInfoDto {

    private String priceName;
    private String vendorName;
    private String senderEmail;
    private String adminCode;
    private String viewCode;
    private String vendorId;
    private String lastUpdated;
    private PriceStatus status;
    private PriceSecurityType securityType;
    private String currentState;
    private Boolean emailIdentification;


    public PriceInfoDto(PriceSenderInfoEntity psi, PriceSecurityType securityType, PriceStatus status) {
        this.lastUpdated = psi.getLastUpdated() != null ? psi.getLastUpdated().toString() : null;
        this.senderEmail = psi.getEmail();
        this.adminCode = psi.getAdminCode();
        this.viewCode = psi.getViewCode();
        this.priceName = psi.getFilePath();
        this.vendorName = psi.getName();
        this.status = status;
        this.securityType = securityType;
        this.currentState = psi.getCurrentState();
        this.vendorId = psi.getVendorId();
        this.emailIdentification = psi.getEmailIdentification();
    }
}
