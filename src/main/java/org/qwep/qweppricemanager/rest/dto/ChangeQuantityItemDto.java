package org.qwep.qweppricemanager.rest.dto;

import lombok.*;

@Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ChangeQuantityItemDto {
    private String brand;
    private String article;
    private String vendorId;
    private Long quantity;

}
