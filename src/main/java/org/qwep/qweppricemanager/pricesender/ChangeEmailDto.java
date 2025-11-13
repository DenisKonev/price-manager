package org.qwep.qweppricemanager.pricesender;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
public class ChangeEmailDto {
    @NotBlank
    private String adminCode;
    @Email
    private String email;
}
