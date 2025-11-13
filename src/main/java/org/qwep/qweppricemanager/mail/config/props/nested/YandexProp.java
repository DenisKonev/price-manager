package org.qwep.qweppricemanager.mail.config.props.nested;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.qwep.qweppricemanager.mail.config.interfaces.IMailProvider;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@Data
@Getter
@Setter
public class YandexProp implements IMailProvider {

    private String user;
    private String password;
    private String host;
    private String protocol;
    private String workdir;

    @NestedConfigurationProperty
    private SmtpProp smtp;

}
