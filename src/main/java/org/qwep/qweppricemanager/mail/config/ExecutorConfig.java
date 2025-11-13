package org.qwep.qweppricemanager.mail.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@Configuration
@RequiredArgsConstructor
public class ExecutorConfig {

    private ExecutorService executorService;

    @Bean
    public ExecutorService taskExecutor() {
        executorService = Executors.newVirtualThreadPerTaskExecutor();
        return executorService;
    }
}
