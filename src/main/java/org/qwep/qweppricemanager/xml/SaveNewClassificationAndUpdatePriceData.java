package org.qwep.qweppricemanager.xml;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.qwep.qweppricemanager.pricedata.PriceDataService;
import org.qwep.qweppricemanager.pricedata.PriceDto;
import org.qwep.qweppricemanager.pricedata.PriceProcessingException;
import org.qwep.qweppricemanager.pricedata.Summary;
import org.qwep.qweppricemanager.pricesender.*;
import org.qwep.qweppricemanager.pricesender.configuration.Configuration;
import org.qwep.qweppricemanager.pricesender.configuration.ConfigurationType;
import org.qwep.qweppricemanager.pricesender.configuration.PriceConfService;
import org.qwep.qweppricemanager.pricesender.enums.PriceState;

import java.net.URL;
import java.util.*;

@Slf4j
public class SaveNewClassificationAndUpdatePriceData implements Runnable {

    public Thread thread;
    private final ObjectMapper objectMapper;
    private final PriceSenderInfoEntity psi;
    private final PriceConfService priceConfService;
    private final PriceDataService priceDataService;
    private final PriceSenderService priceSenderService;
    private List<ClassificationItem> newClassificationItems;
    private final XmlDataProcessorService xmlDataProcessorService;

    public SaveNewClassificationAndUpdatePriceData(String adminCode,
                                                   PriceSenderService priceSenderService,
                                                   PriceSenderInfoEntity psi,
                                                   PriceConfService priceConfService,
                                                   PriceDataService priceDataService,
                                                   List<ClassificationItem> classification,
                                                   XmlDataProcessorService xmlDataProcessorService) {
        this.priceSenderService = priceSenderService;
        this.priceConfService = priceConfService;
        this.priceDataService = priceDataService;
        this.newClassificationItems = classification;
        this.psi = psi;
        this.xmlDataProcessorService = xmlDataProcessorService;
        objectMapper = new ObjectMapper();
        this.thread = new Thread(this, "GetXmlHeaders for adminCode: " + adminCode);
        log.debug("Constructed {}", thread.getName());
    }

    @Override
    public void run() {
        log.info("start {}", thread.getName());
        priceSenderService.updateState(psi.getAdminCode(), PriceState.IN_PROGRESS);
        try {
            List<ClassificationItem> classificationItemsPsi =
                    List.of(objectMapper.readValue(psi.getClassificationListJsonString(), ClassificationItem[].class));
            if (classificationItemsPsi.parallelStream().anyMatch(clitem -> clitem.getCategory() == null)
            ) throw new Exception("classification doesn't match");
            log.debug("ThreadName: {}, classification: {}", thread.getName(), classificationItemsPsi);

            Optional<List<Configuration>> optionalConfigurations =
                    priceConfService.buildConf(psi.getConfigurationsJsonString());
            log.debug("ThreadName: {}, configuration: {}", thread.getName(), optionalConfigurations);

            List<Configuration> confs = List.of(objectMapper.readValue(psi.getConfigurationsJsonString(), Configuration[].class));
            String offstingJson = confs
                    .stream()
                    .filter(c -> c.getType().equals(ConfigurationType.Offsting))
                    .findFirst()
                    .orElseThrow()
                    .getValue();

            List<Integer> offsting = new ArrayList<>(List.of(objectMapper.readValue(offstingJson, Integer[].class)));
            if (offsting.size() > 1) {
                offsting.remove(offsting.size() - 1);
            }
            List<PriceDto> priceDtos = xmlDataProcessorService.parsePriceDtos(
                    new URL(psi.getFilePath()).openStream(),
                    offsting,
                    newClassificationItems,
                    optionalConfigurations,
                    psi.getPriceCurrency());

            if (priceDtos.isEmpty()) {
                log.error("list price dto is empty with {} {}", psi.getEmail(), psi.getAdminCode());
                priceSenderService.updateState(psi.getAdminCode(), PriceState.ERROR_EMPTY);
                return;
            }

            UUID priceTableRef = UUID.randomUUID();
            Summary summary = priceDataService.save(priceDtos, priceTableRef);
            log.info("PriceData saved for adminCode: {} email: {} loaded: {} declined: {}",
                    psi.getAdminCode(),
                    psi.getEmail(),
                    summary.getLoadedDataRowsCount(),
                    summary.getDeclinedDataRowsCount());

            try {
                priceSenderService.rotatePriceDataTable(psi.getAdminCode(),
                        PriceTableRef.of(new String[]{priceTableRef.toString()}));
            } catch (NoPriceTableRefsException exception) {
                priceSenderService.updateUploaded(psi.getAdminCode(), priceTableRef);
            } catch (NoSuchElementException exception) {
                //can't find psi
                priceDataService.dropPriceTable(priceTableRef);
                log.info("drop table");
            }

        } catch (PriceProcessingException exception) {
            priceSenderService.updateState(psi.getAdminCode(), exception.getErrorType());
            if (exception.getErrorType().equals(PriceState.ERROR)) {
                log.error(exception.getMessage());
            }
        } catch (Exception exception) {
            priceSenderService.updateState(psi.getAdminCode(), PriceState.ERROR);
            log.error(exception.getMessage());
        }
        log.info("Finished {}", thread.getName());
    }
}
