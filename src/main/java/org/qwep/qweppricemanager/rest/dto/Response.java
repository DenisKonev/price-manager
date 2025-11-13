package org.qwep.qweppricemanager.rest.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Response<E> {
    private E entity;
    private boolean success;
    private String message;

    public Response(boolean success, String message) {
        entity = null;
        this.success = success;
        this.message = message;
    }
}
