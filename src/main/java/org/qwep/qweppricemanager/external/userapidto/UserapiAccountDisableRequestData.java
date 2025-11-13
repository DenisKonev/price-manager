package org.qwep.qweppricemanager.external.userapidto;

import lombok.*;

import java.util.List;

@Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserapiAccountDisableRequestData {
    private List<UserapiAccountToDisable> accounts;
}
