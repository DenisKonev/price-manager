//package org.qwep.qweppricemanager.pricedata;
//
//import com.zaxxer.hikari.HikariDataSource;
//import org.springframework.boot.context.properties.ConfigurationProperties;
//import org.springframework.boot.jdbc.DataSourceBuilder;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.context.annotation.Scope;
//
//@Configuration
//public class Config {
//
//    @Bean
//    @Scope(value = "prototype")
//    @ConfigurationProperties("spring.datasource.hikari")
//    public HikariDataSource hikariPostgresDataSource() {
//        return DataSourceBuilder.create().type(HikariDataSource.class).build();
//    }
//}
