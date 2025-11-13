package org.qwep.qweppricemanager.conversion;

import lombok.extern.slf4j.Slf4j;
import org.qwep.qweppricemanager.pricedata.PriceDataService;
import org.qwep.qweppricemanager.pricedata.PriceDto;
import org.qwep.qweppricemanager.pricesender.NoPriceTableRefsException;
import org.qwep.qweppricemanager.pricesender.PriceSenderInfoEntity;
import org.qwep.qweppricemanager.pricesender.PriceSenderService;
import org.qwep.qweppricemanager.pricesender.PriceTableRef;
import org.qwep.qweppricemanager.pricesender.configuration.Configuration;
import org.qwep.qweppricemanager.pricesender.enums.PriceState;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Slf4j
public class CurrencyConversionRunnable implements Runnable {

    private final PriceSenderService priceSenderService;
    private final PriceDataService priceDataService;
    private final CurrencyConversionService currencyConversionService;
    private final Configuration configuration;
    private final PriceSenderInfoEntity psi;
    public Thread thread;

    public CurrencyConversionRunnable(PriceSenderService priceSenderService,
                                      PriceDataService priceDataService,
                                      CurrencyConversionService currencyConversionService,
                                      Configuration configuration,
                                      PriceSenderInfoEntity psi) {
        this.priceSenderService = priceSenderService;
        this.priceDataService = priceDataService;
        this.currencyConversionService = currencyConversionService;
        this.configuration = configuration;
        this.psi = psi;
        this.thread = new Thread(this, "CurrencyConversionRunnable with admin code: " + psi.getAdminCode());
    }


    @Override
    public void run() {
        try {
            List<PriceDto> priceDtos = priceDataService.getPriceDtos(psi.getPriceTableRefs());
            List<PriceDto> newPriceDtos = priceDtos
                    .parallelStream()
                    .map(priceDto -> {
                        CurrencyUnit newCurrency = Monetary.getCurrency(configuration.getValue());
                        CurrencyUnit oldCurrency = Monetary.getCurrency(priceDto.getCurrency());
                        CurrencyUnit rubCurrency = Monetary.getCurrency("RUB");

                        priceDto.setPrice(currencyConversionService
                                .convert(priceDto.getPrice(), rubCurrency, oldCurrency));
                        priceDto.setCurrency(newCurrency.getCurrencyCode());
                        priceDto.setPrice(currencyConversionService
                                .convert(priceDto.getPrice(),
                                        newCurrency,
                                        Monetary.getCurrency(psi.getPriceCurrency())));
                        priceDto.setCurrency(newCurrency.getCurrencyCode());
                        return priceDto;
                    })
                    .toList();
            UUID priceTableRef = UUID.randomUUID();
            priceDataService.save(newPriceDtos, priceTableRef);
            try {
                String[] priceTableRefs = new String[]{priceTableRef.toString()};
                psi.setPriceTableRefs(priceTableRefs);
                psi.setCurrentState(PriceState.CHANGED.state);
                priceSenderService.rotatePriceDataTable(psi.getAdminCode(), PriceTableRef.of(priceTableRefs));
            } catch (NoPriceTableRefsException exception) {
                priceSenderService.updateUploaded(psi.getAdminCode(), priceTableRef);
            } catch (NoSuchElementException exception) {
                priceDataService.dropPriceTable(priceTableRef);
            }
            psi.setCurrentState(PriceState.CHANGED.state);
            priceSenderService.savePriceSenderInfoEntity(psi);
        } catch (Exception e) {
            psi.setCurrentState(PriceState.CHANGED.state);
            priceSenderService.savePriceSenderInfoEntity(psi);
            log.error("currency translation error: {}", e.getMessage());
        }
    }
}
