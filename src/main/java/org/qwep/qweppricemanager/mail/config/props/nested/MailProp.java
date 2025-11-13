package org.qwep.qweppricemanager.mail.config.props.nested;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@Data
@Getter
@Setter
public class MailProp {
    @NestedConfigurationProperty
    private YandexProp yandex;

}
