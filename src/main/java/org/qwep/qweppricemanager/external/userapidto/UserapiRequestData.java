package org.qwep.qweppricemanager.external.userapidto;

import lombok.*;

import java.util.List;

@Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserapiRequestData {

    private List<UserapiAccount> accounts;
}
