package org.qwep.qweppricemanager.commons.requestmetrics;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Message<T, S> {
    private S schema;
    private T payload;
}