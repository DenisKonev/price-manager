package org.qwep.qweppricemanager.rest.dto;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class UniversalResponseDto {

    private Boolean success;
    private String message;
}
