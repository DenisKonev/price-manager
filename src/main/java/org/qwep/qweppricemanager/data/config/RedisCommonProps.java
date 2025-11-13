package org.qwep.qweppricemanager.data.config;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public abstract class RedisCommonProps {

    private String host;
    private Integer port;
    private String password;
    private Integer timeout;
    private Integer database;
    private Integer connectTimeout;
}
