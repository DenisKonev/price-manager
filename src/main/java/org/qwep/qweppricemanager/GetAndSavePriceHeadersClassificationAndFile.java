package org.qwep.qweppricemanager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.poi.hssf.OldExcelFormatException;
import org.qwep.qweppricemanager.pricedata.PriceFileStream;
import org.qwep.qweppricemanager.pricedata.PriceProcessingException;
import org.qwep.qweppricemanager.pricedata.fileconverter.Book;
import org.qwep.qweppricemanager.pricedata.fileconverter.BookBuilder;
import org.qwep.qweppricemanager.pricefile.FileSaverThread;
import org.qwep.qweppricemanager.pricesender.ClassificationItem;
import org.qwep.qweppricemanager.pricesender.PriceSenderService;
import org.qwep.qweppricemanager.pricesender.enums.PriceState;

import java.util.List;

@Slf4j
public class GetAndSavePriceHeadersClassificationAndFile implements Runnable {
    private final String adminCode;
    private final PriceFileStream priceFileStream;
    private final BookBuilder bookBuilder;
    private final PriceSenderService priceSenderService;
    private final FileSaverThread fileSaverThread;
    private final ObjectMapper mapper;
    public Thread thread;

    public GetAndSavePriceHeadersClassificationAndFile(BookBuilder bookBuilder,
                                                       String adminCode,
                                                       PriceFileStream priceFileStream,
                                                       FileSaverThread fileSaverThread,
                                                       PriceSenderService priceSenderService) {

        this.adminCode = adminCode;
        this.bookBuilder = bookBuilder;
        this.priceFileStream = priceFileStream;
        this.fileSaverThread = fileSaverThread;
        this.priceSenderService = priceSenderService;
        this.mapper = new ObjectMapper();
        this.thread = new Thread(this, "GetAndSetPriceHeadersClassification for adminCode: " + adminCode);
        log.debug("Constructed {}", thread.getName());
    }

    @Override
    public void run() {
        log.info("Started: {}", thread.getName());
        try {
            fileSaverThread.thread.start();
            Book book = bookBuilder.build(
                    priceFileStream.getFileName(),
                    priceFileStream.getFileType(),
                    priceFileStream.getInputStream()
            );
            List<ClassificationItem> classificationItems = book.getClassification();
            String classificationJson = mapper.writeValueAsString(classificationItems);
            priceSenderService.setClassification(adminCode, classificationJson);
            log.info("Filed priceHeaders for adminCode: {}", adminCode);
            fileSaverThread.thread.join();

            priceSenderService.updateState(adminCode, PriceState.AWAITS_CLASSIFICATION);
            log.info("{} awaiting classification", adminCode);

        } catch (OldExcelFormatException exception) {
            log.error("File has old format for adminCode: {} filename: {} with exception: {} stackTrace:{}",
                    adminCode,
                    priceFileStream.getFileName(),
                    exception.getMessage(),
                    ExceptionUtils.getStackTrace(exception)
            );
            priceSenderService.updateState(adminCode, PriceState.ERROR_95);
        } catch (PriceProcessingException exception) {
            log.info(
                    "Can't process registering for adminCode: {} filename: {} " +
                            "with error type: {} exception: {} stackTrace:{}",
                    adminCode,
                    priceFileStream.getFileName(),
                    exception.getErrorType(),
                    exception.getMessage(),
                    ExceptionUtils.getStackTrace(exception));
            if (exception.getErrorType().equals(PriceState.ERROR)) {
                log.error("Can't process registering for adminCode: {} filename: {} with exception: {} stackTrace:{}",
                        adminCode,
                        priceFileStream.getFileName(),
                        exception.getMessage(),
                        ExceptionUtils.getStackTrace(exception)
                );
            }
            priceSenderService.updateState(adminCode, exception.getErrorType());
        } catch (JsonProcessingException | InterruptedException exception) {
            priceSenderService.updateState(adminCode, PriceState.ERROR);
            log.error("Can't process registering for adminCode: {} filename: {} with exception: {} stackTrace:{}",
                    adminCode,
                    priceFileStream.getFileName(),
                    exception.getMessage(),
                    ExceptionUtils.getStackTrace(exception)
            );
        }
        log.info("Finished {}", thread.getName());
    }
}

