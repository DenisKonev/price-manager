package org.qwep.qweppricemanager.external.userapidto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserapiRequest {
    @JsonProperty("RequestData")
    private UserapiRequestData requestData;
}
