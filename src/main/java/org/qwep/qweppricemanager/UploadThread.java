package org.qwep.qweppricemanager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.poi.hssf.OldExcelFormatException;
import org.qwep.qweppricemanager.mail.MailSender;
import org.qwep.qweppricemanager.pricedata.PriceDataService;
import org.qwep.qweppricemanager.pricedata.PriceDto;
import org.qwep.qweppricemanager.pricedata.PriceFileStream;
import org.qwep.qweppricemanager.pricedata.PriceProcessingException;
import org.qwep.qweppricemanager.pricedata.Summary;
import org.qwep.qweppricemanager.pricedata.fileconverter.Book;
import org.qwep.qweppricemanager.pricedata.fileconverter.BookBuilder;
import org.qwep.qweppricemanager.pricesender.ClassificationItem;
import org.qwep.qweppricemanager.pricesender.PriceSenderInfoEntity;
import org.qwep.qweppricemanager.pricesender.PriceSenderService;
import org.qwep.qweppricemanager.pricesender.PriceTableRef;
import org.qwep.qweppricemanager.pricesender.configuration.Configuration;
import org.qwep.qweppricemanager.pricesender.configuration.PriceConfService;
import org.qwep.qweppricemanager.pricesender.enums.PriceState;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
public class UploadThread implements Runnable {

    private final BookBuilder bookBuilder;
    private final PriceSenderService priceSenderService;
    private final PriceConfService priceConfService;
    private final PriceFileStream priceFileStream;
    private final PriceSenderInfoEntity psi;
    private final ObjectMapper mapper;
    private final PriceDataService priceDataService;
    private final MailSender mailSender;

    public UploadThread(PriceConfService priceConfService,
                        PriceFileStream priceFileStream,
                        PriceSenderInfoEntity psi,
                        BookBuilder bookBuilder,
                        PriceDataService priceDataService,
                        PriceSenderService priceSenderService,
                        MailSender mailSender) {
        this.priceConfService = priceConfService;
        this.priceFileStream = priceFileStream;
        this.psi = psi;
        this.mailSender = mailSender;
        this.bookBuilder = bookBuilder;
        this.priceDataService = priceDataService;
        this.priceSenderService = priceSenderService;
        this.thread = new Thread(this, "UploadThread for adminCode: " + psi.getAdminCode());
        this.mapper = new ObjectMapper();
        log.debug("Constructed {}", thread.getName());
    }

    public Thread thread;

    /**
     * Основной метод потока: запускает цепочку обработки прайс-листа,
     * сохраняет данные и отправляет уведомления.
     */
    @Override
    public void run() {
        log.info("Started: {}", thread.getName());
        try {
            updatePriceState();
            Book book = buildBook();
            log.info("Begin processing file: {}", book.getName());
            psi.setFilePath(book.getName());

            List<ClassificationItem> classificationItems = parseClassification();
            validateClassification(classificationItems, book);
            log.debug("ThreadName: {}, classification: {}", thread.getName(), classificationItems);

            Optional<List<Configuration>> configurations = buildConfigurations();
            List<PriceDto> priceDtos = book.getPriceDtos(
                    classificationItems,
                    configurations,
                    psi.getPriceCurrency()
            );
            log.debug("ThreadName: {}, priceDtos size: {}", thread.getName(), priceDtos.size());

            if (priceDtos.isEmpty()) {
                handleEmptyPrices();
                return;
            }

            handleNonEmptyPrices(priceDtos);

        } catch (OldExcelFormatException e) {
            handleOldExcelFormat(e);
        } catch (PriceProcessingException e) {
            handlePriceProcessingException(e);
        } catch (Exception e) {
            handleGeneralException(e);
        } finally {
            log.info("Finished {}", thread.getName());
        }
    }

    /**
     * Устанавливает состояние IN_PROGRESS перед началом обработки.
     */
    private void updatePriceState() {
        priceSenderService.updateState(psi.getAdminCode(), PriceState.IN_PROGRESS);
    }

    /**
     * Создаёт и возвращает объект Book из входного потока.
     */
    private Book buildBook() throws OldExcelFormatException, PriceProcessingException {
        return bookBuilder.build(
                priceFileStream.getFileName(),
                priceFileStream.getFileType(),
                priceFileStream.getInputStream()
        );
    }

    /**
     * Парсит JSON-строку классификаций в список объектов.
     */
    private List<ClassificationItem> parseClassification() throws JsonProcessingException {
        return List.of(mapper.readValue(
                psi.getClassificationListJsonString(),
                ClassificationItem[].class
        ));
    }

    /**
     * Проверяет соответствие классификаций из PSI тем, что в книге.
     */
    private void validateClassification(List<ClassificationItem> items, Book book) throws Exception {
        boolean hasNullCategory = items.parallelStream().anyMatch(i -> i.getCategory() == null);
        if (hasNullCategory && book.getClassification().size() == items.size()) {
            throw new Exception("classification doesn't match");
        }
    }

    /**
     * Строит конфигурации из JSON-строки.
     */
    private Optional<List<Configuration>> buildConfigurations() {
        Optional<List<Configuration>> optionalConfigurations = priceConfService.buildConf(psi.getConfigurationsJsonString());
        log.debug("ThreadName: {}, configuration: {}", thread.getName(), optionalConfigurations);

        return optionalConfigurations;
    }

    /**
     * Обрабатывает ситуацию с пустым набором DTO:
     * помечает состояние, отправляет письмо об ошибке.
     */
    private void handleEmptyPrices() {
        priceSenderService.updateState(psi.getAdminCode(), PriceState.ERROR_EMPTY);
        sendErrorMessage();
    }

    /**
     * Обрабатывает ненулевой набор DTO:
     * сохраняет данные, поворачивает таблицу и отправляет сводку.
     */
    private void handleNonEmptyPrices(List<PriceDto> priceDtos) {
        PriceTableRef ref = PriceTableRef.of(new String[]{UUID.randomUUID().toString()});
        Summary summary = priceDataService.save(priceDtos, ref.getPriceTableRef());
        log.info("PriceData saved for adminCode: {} email: {} loaded: {} declined: {}",
                psi.getAdminCode(),
                psi.getEmail(),
                summary.getLoadedDataRowsCount(),
                summary.getDeclinedDataRowsCount()
        );
        priceSenderService.rotatePriceDataTable(psi.getAdminCode(), ref);
        sendPriceSummaryMessage(summary);
    }

    /**
     * Обработчик исключения OldExcelFormatException:
     * помечает состояние ERROR_95, логирует и отправляет письмо.
     */
    private void handleOldExcelFormat(OldExcelFormatException e) {
        priceSenderService.updateState(psi.getAdminCode(), PriceState.ERROR_95);
        log.error("Can't process registering for adminCode: {} filename: {} with exception: {}",
                psi.getAdminCode(),
                priceFileStream.getFileName(),
                e.getMessage()
        );
        sendErrorMessage();
    }

    /**
     * Обработчик PriceProcessingException:
     * помечает состояние по типу ошибки, логирует при ERROR, отправляет письмо.
     */
    private void handlePriceProcessingException(PriceProcessingException e) {
        priceSenderService.updateState(psi.getAdminCode(), e.getErrorType());
        if (e.getErrorType().equals(PriceState.ERROR)) {
            log.error("Can't process registering for adminCode: {} filename: {} with exception: {} stackTrace:{}",
                    psi.getAdminCode(),
                    priceFileStream.getFileName(),
                    e.getMessage(),
                    ExceptionUtils.getStackTrace(e)
            );
        }
        sendErrorMessage();
    }

    /**
     * Общий обработчик всех прочих исключений:
     * помечает состояние ERROR, логирует и отправляет письмо.
     */
    private void handleGeneralException(Exception e) {
        priceSenderService.updateState(psi.getAdminCode(), PriceState.ERROR);
        log.error("Can't process registering for adminCode: {} filename: {} with exception: {} stackTrace:{}",
                psi.getAdminCode(),
                priceFileStream.getFileName(),
                e.getMessage(),
                ExceptionUtils.getStackTrace(e)
        );
        sendErrorMessage();
    }

    /**
     * Безопасная отправка письма об ошибке:
     * в случае SMTPSendFailedException — логируем WARN и продолжаем.
     */
    private void sendErrorMessage() {
        try {
            mailSender.sendErrorMessage(psi.getEmail(), psi.getAdminCode());
        } catch (Exception e) {
            log.warn("Failed to send error notification for adminCode: {}. Reason: {}",
                    psi.getAdminCode(), e.getMessage());
        }
    }

    /**
     * Безопасная отправка сводного письма:
     * в случае SMTPSendFailedException — логируем WARN и продолжаем.
     *
     * @param summary результат обработки прайс-листа
     */
    private void sendPriceSummaryMessage(Summary summary) {
        try {
            mailSender.sendPriceSummary(
                    psi.getEmail(),
                    psi.getFilePath(),
                    summary,
                    psi.getAdminCode()
            );
        } catch (Exception e) {
            log.warn("Failed to send summary notification for adminCode: {}. Reason: {}",
                    psi.getAdminCode(), e.getMessage());
        }
    }
}
