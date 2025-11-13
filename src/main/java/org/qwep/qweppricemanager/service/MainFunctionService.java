package org.qwep.qweppricemanager.service;

import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.qwep.qweppricemanager.commons.UserTextsConfig;
import org.qwep.qweppricemanager.mail.service.CoreProcessorService;
import org.qwep.qweppricemanager.pricedata.PriceDto;
import org.qwep.qweppricemanager.rest.dto.BasketAddItemDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MainFunctionService {

    private final CoreProcessorService coreProcessorService;
    private final UserTextsConfig userTextsConfig;

    @Autowired
    public MainFunctionService(
            CoreProcessorService coreProcessorService,
            UserTextsConfig userTextsConfig) {
        this.coreProcessorService = coreProcessorService;
        this.userTextsConfig = userTextsConfig;
    }


    public void sendBasketAddMessage(PriceDto item, BasketAddItemDto basketAddItemDto, String vendorEmail, String vendorName) throws MessagingException {
        String message = userTextsConfig.getBasketAddItemText().
                replace("${0}", basketAddItemDto.getClient()).
                replace("${1}", basketAddItemDto.getEmail()).
                replace("${2}", basketAddItemDto.getPhone()).
                replace("${3}", item.getArticle()).
                replace("${4}", item.getBrand()).
                replace("${5}", item.getPartname()).
                replace("${6}", item.getPrice()).
                replace("${7}", item.getWarehouse()).
                replace("${8}", vendorName);
        coreProcessorService.sendMessageToClient(vendorEmail, message);
    }


}
