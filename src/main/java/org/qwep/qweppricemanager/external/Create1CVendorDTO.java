package org.qwep.qweppricemanager.external;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.qwep.qweppricemanager.pricesender.Add1CPriceDTO;

@AllArgsConstructor
@Setter
@Getter
@NoArgsConstructor
public class Create1CVendorDTO {
    private String vendorName;
    private String token;
    private String vendorSite;
    private String vendorMail;

    public Create1CVendorDTO(Add1CPriceDTO add1CPriceDTO) {
        this.vendorName = add1CPriceDTO.getVendorName();
        this.token = add1CPriceDTO.getToken();
        this.vendorSite = add1CPriceDTO.getVendorSite();
        this.vendorMail = add1CPriceDTO.getVendorMail();
    }
}
