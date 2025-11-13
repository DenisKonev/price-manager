package org.qwep.qweppricemanager.service;

import lombok.extern.slf4j.Slf4j;
import org.qwep.qweppricemanager.pricesender.PriceSenderService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.nio.charset.Charset;

@Configuration
@EnableScheduling
@Slf4j
@EnableAsync
public class MainFunctionConfiguration implements CommandLineRunner {

    private final RabbitTemplate rabbitTemplate;
    private final PriceSenderService priceSenderService;
    @Value("${cron.enabled}")
    private Boolean cronEnabled;


    @Autowired
    public MainFunctionConfiguration(RabbitTemplate rabbitTemplate,
                                     PriceSenderService priceSenderService
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.priceSenderService = priceSenderService;

    }


    @Scheduled(cron = "0 0 */12 * * ?") //every 12 hours
    public void sendPriceLifecycleValidationOrder() {
        if(cronEnabled) {
            log.info("trigger new price lifecycle validation order");
            rabbitTemplate.convertAndSend("q.price.lifecycle.validation.orders", "price price lifecycle validation order");
        }
    }


    //@LogExecTimeAspect
    @RabbitListener(queues = "q.price.lifecycle.validation.orders")
    public void handlePriceLifecycleValidationOrder() {
        log.info("performing price lifecycle validation");
        try {
            priceSenderService.validatePriceData();
        } catch (Exception exception) {
            log.error("faild to validate price data: {}", exception.getMessage());
        }
    }


    //@LogExecTimeAspect
    @Override
    public void run(String... args) throws Exception {
        log.info("run MAIN FUNCTION on app startup");
        //schedulerService.triggerMainFunction(); #to force this shit on app startup
        log.info("charset is: '{}'", Charset.defaultCharset());
        log.info("finish run MAIN FUNCTION on app startup");
    }
}
