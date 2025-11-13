package org.qwep.qweppricemanager.rest.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@EqualsAndHashCode
public class AddPriceDto {
    private String accessToken;
    @NotBlank(message = "Name may not be blank")
    private String name;
    @NotBlank(message = "Email may not be blank")
    private String email;
    @NotBlank(message = "Currency may not be blank")
    private String currency;
}
