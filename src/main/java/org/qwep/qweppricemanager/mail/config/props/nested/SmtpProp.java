package org.qwep.qweppricemanager.mail.config.props.nested;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
public class SmtpProp {
    private String host;
    private String port;
    private String protocol;
}
