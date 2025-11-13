package org.qwep.qweppricemanager.external;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

@Configuration
@Getter
public class DbConfig {

    @Value("${qwep.dbs.api.qwep.url}")
    private String apiQwepDbUrl;

    @Value("${qwep.dbs.api.qwep.priceDev}")
    private String priceDevDb;

    @Bean(value = "apiQwep")
    public Connection getApiQwepConnection() throws SQLException {
        return DriverManager.getConnection(apiQwepDbUrl);
    }

    @Bean(value = "priceDb")
    public Connection getApiQwepDevConnection() throws SQLException {
        return DriverManager.getConnection(priceDevDb);
    }
}
