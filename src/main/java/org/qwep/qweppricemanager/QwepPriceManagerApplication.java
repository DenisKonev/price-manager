package org.qwep.qweppricemanager;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.util.IOUtils;
import org.qwep.qweppricemanager.pricedata.fileconverter.CharsetDetector;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

import javax.annotation.PostConstruct;
import java.util.TimeZone;

@Slf4j
@SpringBootApplication
@OpenAPIDefinition(info = @Info(
        title = "QWEP-price",
        description = "Сервис управления прайсов QWEP",
        version = "0.1",
        contact = @Contact(
                name = "Кирилл Кошаев",
                url = "https://qwep.ru",
                email = "k.koshaev@qwep.ru")))
public class QwepPriceManagerApplication {


    @Value("${spring.rabbitmq.host}")
    String host;

    @Value("${spring.rabbitmq.username}")
    String login;

    @Value("${spring.rabbitmq.password}")
    String password;

    @Value("${spring.rabbitmq.port}")
    String port;

    public static void main(String[] args) {
        SpringApplication.run(QwepPriceManagerApplication.class, args);
    }

    @PostConstruct
    public void init() {
        IOUtils.setByteArrayMaxOverride(1000000000);
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Moscow"));
    }

    @Bean
    public CharsetDetector charsetDetector() {
        String[] charsetsToBeTested = new String[]{"UTF-8", "windows-1251"};
        return new CharsetDetector(charsetsToBeTested);
    }

    @Bean
    public MethodValidationPostProcessor methodValidationPostProcessor() {
        return new MethodValidationPostProcessor();
    }

    @Bean
    CachingConnectionFactory connectionFactory() {
        CachingConnectionFactory cachingConnectionFactory = new CachingConnectionFactory(host);
        cachingConnectionFactory.setUsername(login);
        cachingConnectionFactory.setPassword(password);
        cachingConnectionFactory.setPort(Integer.parseInt(port));
        return cachingConnectionFactory;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        return new RabbitTemplate(connectionFactory);
    }

    @Bean("senderNotificationsQueue")
    public Queue senderNotificationsQueue() {
        return new Queue("q.price.loading.summary.orders", false);
    }

    @Bean("priceLoadingOrdersQueue")
    public Queue priceLoadingOrdersQueue() {
        return new Queue("q.price.loading.orders", false);
    }

    @Bean("priceLifecycleValidationOrdersQueue")
    public Queue priceLifecycleValidationOrdersQueue() {
        return new Queue("q.price.lifecycle.validation.orders", false);
    }

    @Bean("priceProcessingAwaitingQueue")
    public Queue priceProcessingAwaitingQueue() {
        return new Queue("q.price.processing.awaiting", false);
    }

    @Bean("viewCodeUpdateOrdersQueue")
    public Queue viewCodeUpdateOrdersQueue() {
        return new Queue("q.viewCode.update.orders", false);
    }
}
