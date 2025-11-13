package org.qwep.qweppricemanager.commons;

import lombok.Data;
import lombok.Getter;
import org.qwep.qweppricemanager.mail.config.boilerplate.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "qwep-text")
@PropertySource(value = "classpath:user-texts.yaml", factory = YamlPropertySourceFactory.class)
@Data
@Getter
public class UserTextsConfig {

    private String priceLifecycleOverWarning;
    private String priceRemoveWrongMessages;
    private String emailTitle;
    private String priceDropNotification;
    private String priceLoadingResultsSummary;
    private String priceUploadedSuccessfully;
    private String emailOrNameRequestParamMissing;
    private String priceUploadedBadly;
    private String vendorRegistrationFailed;
    private String vendorNotFound;
    private String vendorRemovalSuccess;
    private String xmlPriceBindingSuccess;
    private String invalidXmlPriceUrl;
    private String classificationInvalidSize;
    private String classificationNotFound;
    private String classificationParsingException;
    private String priceImportSuccess;
    private String priceImportFail;
    private String basketAddItemText;
    private String userBasketAddNotificationFailed;
    private String basketSizeLimitReached;
    private String basketOrderCreatedOk;
    private String vendorIsVerified;
    private String registeredOkAndNeedClassification;
    private String priceRegisterFailed;
    private String defaultPriceLoadingText;
    private String zipLoadingError;
    private String brandNormalizationWarn;
    private String cancelWritePriceData;
    private String pricePreloadingFinished;
    private String failedPricePreloading;
    private String priceDuplicatedLoadingWarn;
    private String priceHeaderRowFound;
    private String notFoundPriceHeaderRow;
}
