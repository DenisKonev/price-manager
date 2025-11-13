package org.qwep.qweppricemanager.pricedata;

import lombok.Data;
import lombok.Getter;
import org.qwep.qweppricemanager.mail.config.boilerplate.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "price-query")
@PropertySource(value = "classpath:price-queries.yaml", factory = YamlPropertySourceFactory.class)
@Data
@Getter
public class PriceQueriesConfig {
    private String createEmptyPriceTableQuery;
    private String insertPriceQuery;
    private String dropPriceTableQuery;
    private String changeItemQuantity;
    private String checkIfExist;
    private String deleteRow;
    private String getItemQuantity;
    private String countRowsQuery;
    private String getPriceDtos;
}
