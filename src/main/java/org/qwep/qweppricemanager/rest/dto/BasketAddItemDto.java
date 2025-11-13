package org.qwep.qweppricemanager.rest.dto;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class BasketAddItemDto {

    private Long itemId;
    private String[] refTable;
    private String client;
    private String phone;
    private String email;
    private String comment;
}
