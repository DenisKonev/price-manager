package org.qwep.qweppricemanager.rest;

import lombok.extern.slf4j.Slf4j;
import org.qwep.qweppricemanager.mail.service.CoreProcessorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@RequestMapping("/test")
public class TestController {

    private final CoreProcessorService coreProcessorService;


    @Autowired
    public TestController(CoreProcessorService coreProcessorService) {
        this.coreProcessorService = coreProcessorService;

    }

    @GetMapping("/sendLifeCirecleSomeShit")
    public void sendLifeCircleShit() {
        coreProcessorService.sendPriceLifecycleOverWarningToClient(
                "s.panin@qwep.ru", "admin123123123Code");
    }

    @GetMapping("/logError")
    public void logError() {
        log.error("This is test error");
    }

    @GetMapping("/serega")
    public String stringySeregi() {
        return "sexy";
    }

    @GetMapping("/xml")
    public String getXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Items>
                    <Item>
                        <Марка>brandTest222333</Марка>
                        <Артикул>articleTest222333</Артикул>
                        <Название>PartnameTest222333</Название>
                        <Количество>123</Количество>
                        <Цена>99,99</Цена>
                    </Item>
                    <Item>
                        <Марка>brandTest222334</Марка>
                        <Артикул>articleTest222334</Артикул>
                        <Название>PartnameTest222334</Название>
                        <Количество>124</Количество>
                        <Цена>100,99</Цена>
                    </Item>
                </Items>
                """;
    }

    @GetMapping("/throwError")
    public void throwError() {
        throw new RuntimeException("THIS IS A TEST ERROR. PLEASE IGNORE IT!");
    }
}
