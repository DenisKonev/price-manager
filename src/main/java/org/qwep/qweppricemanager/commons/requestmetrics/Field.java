package org.qwep.qweppricemanager.commons.requestmetrics;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Field {

    @JsonProperty("field")
    private String fieldName;

    @JsonProperty("type")
    private String fieldType;

    private Boolean optional;
}