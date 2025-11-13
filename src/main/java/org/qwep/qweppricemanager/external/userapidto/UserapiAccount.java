package org.qwep.qweppricemanager.external.userapidto;

import lombok.*;

@Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserapiAccount {
    private String vid;
    private String login;
    private String password;
}
