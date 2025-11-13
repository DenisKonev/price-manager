package org.qwep.qweppricemanager.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.mail.MessagingException;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.qwep.qweppricemanager.GetAndSavePriceHeadersClassificationAndFile;
import org.qwep.qweppricemanager.UploadThread;
import org.qwep.qweppricemanager.commons.UserTextsConfig;
import org.qwep.qweppricemanager.conversion.CurrencyConversionRunnable;
import org.qwep.qweppricemanager.conversion.CurrencyConversionService;
import org.qwep.qweppricemanager.data.Core;
import org.qwep.qweppricemanager.external.ApiQwepService;
import org.qwep.qweppricemanager.external.DataApiService;
import org.qwep.qweppricemanager.external.FreshVendorMetaDto;
import org.qwep.qweppricemanager.external.UserApiService;
import org.qwep.qweppricemanager.mail.MailSender;
import org.qwep.qweppricemanager.mail.config.GracefullConfig;
import org.qwep.qweppricemanager.pricedata.PriceDataService;
import org.qwep.qweppricemanager.pricedata.PriceDto;
import org.qwep.qweppricemanager.pricedata.PriceFileStream;
import org.qwep.qweppricemanager.pricedata.PriceProcessingException;
import org.qwep.qweppricemanager.pricedata.fileconverter.Book;
import org.qwep.qweppricemanager.pricedata.fileconverter.BookBuilder;
import org.qwep.qweppricemanager.pricefile.FileSaverThread;
import org.qwep.qweppricemanager.pricefile.PriceFile;
import org.qwep.qweppricemanager.pricefile.PriceFileService;
import org.qwep.qweppricemanager.pricesender.ClassificationItem;
import org.qwep.qweppricemanager.pricesender.PriceSenderInfoEntity;
import org.qwep.qweppricemanager.pricesender.PriceSenderService;
import org.qwep.qweppricemanager.pricesender.configuration.Configuration;
import org.qwep.qweppricemanager.pricesender.configuration.ConfigurationType;
import org.qwep.qweppricemanager.pricesender.configuration.PriceConfService;
import org.qwep.qweppricemanager.pricesender.enums.PriceState;
import org.qwep.qweppricemanager.rest.dto.*;
import org.qwep.qweppricemanager.search.NoSuchVendorException;
import org.qwep.qweppricemanager.search.SearchService;
import org.qwep.qweppricemanager.search.exception.InProgressStateException;
import org.qwep.qweppricemanager.service.MainFunctionService;
import org.qwep.qweppricemanager.service.NotificationService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailSendException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@Slf4j
@RequestMapping("/api/v3/price")
@Validated
@RequiredArgsConstructor
public class PriceDataV3Controller {

    public static final String BEARER = "Bearer || ";
    public static final String IN_PROGRESS_TRY_LATER = "Price is in progress, try later";
    private final DataApiService dataApiService;
    private final SearchService searchService;
    private final Core core;
    private final MainFunctionService mainFunctionService;
    private final UserTextsConfig userTextsConfig;
    private final ObjectMapper mapper;
    private final NotificationService notificationService;
    private final PriceConfService priceConfService;
    private final ApiQwepService apiQwepService;
    private final PriceSenderService priceSenderService;
    private final UserApiService userApiService;
    private final PriceFileService priceFileService;
    private final BookBuilder bookBuilder;
    private final PriceDataService priceDataService;
    private final MailSender mailSender;
    private final CurrencyConversionService currencyConversionService;
    private final GracefullConfig gracefullConfig;
    private final ExecutorService executorService;


    @ExceptionHandler(value = Exception.class)
    public ResponseEntity<UniversalResponseDto> handleException(Exception e) {
        return ResponseEntity.badRequest().body(new UniversalResponseDto(false, e.getLocalizedMessage()));
    }

    @PostMapping("/search")
    @Operation(summary = "Поиск позиций по прайс-листам.")
    @ApiResponse(responseCode = "200", description = "Результаты поиска.",
            content = {@Content(mediaType = "application/json", schema = @Schema(implementation = PriceDto.class))})
    @ApiResponse(responseCode = "400", description = "Запрос выполнен с ошибками.", content = @Content)
    public ResponseEntity<List<PriceDto>> searchWithCrosses(
            @RequestBody BrandArticleDto requestBody,
            @RequestHeader(value = "X-Vendor-Id", required = false) UUID vendorId) {
        log.debug("/api/v3/price/search  vendor id: {}, brand: {}, article: {}", vendorId, requestBody.getBrand(), requestBody.getArticle());

        List<BrandArticleDto> crosses = dataApiService.getCrossesBy(requestBody.getBrand(), requestBody.getArticle());
        List<PriceDto> prices = List.of();

        try {
            prices = new ArrayList<>(searchService.getPrices(crosses, Optional.ofNullable(vendorId), true));
            if (prices.isEmpty()) {
                prices.addAll(searchService.getPrices(List.of(requestBody), Optional.ofNullable(vendorId), false));
            }
        } catch (InProgressStateException e) {
            log.debug("Search during price update, vendorId: {}", vendorId);
            searchService.getPrices(crosses, Optional.ofNullable(vendorId), true);
        } catch (NoSuchVendorException e) {
            log.debug(e.getMessage());
        } catch (Exception e) {
            log.error("Something went wrong in PriceDataV3Controller.search, vendorId {} error: {}", vendorId, e.getMessage());
            return ResponseEntity.ok(prices);
        } finally {
            log.debug("handle request POST /search by brand: '{}' and article: '{}' with '{}' result items for '{}' input cross items on vendor: '{}'",
                    requestBody.getBrand(),
                    requestBody.getArticle(),
                    prices.size(),
                    crosses.size(),
                    vendorId);

            log.debug("answer /api/v3/price/search  vendor id: {}, brand: {}, article: {} and answer: {} ",
                    vendorId, requestBody.getBrand(), requestBody.getArticle(), prices);
        }

        return ResponseEntity.ok(prices);
    }

    @PostMapping(value = "/file/register", consumes = {"multipart/form-data"})
    @Operation(summary = "Регистрация нового прайс-листа.")
    @ApiResponse(responseCode = "200", description = "Новый прайс-лист зарегистрирован в QWEP и ожидает классификацию.",
            content = {@Content(mediaType = "text", schema = @Schema(implementation = UniversalResponseDto.class))})
    @ApiResponse(responseCode = "400", description = "Запрос выполнен с ошибками.", content = @Content)
    public ResponseEntity<UniversalResponseDto> fileRegister(
            @RequestParam(value = "price-list-file") MultipartFile multipart,
            @RequestParam(value = "vendor-email", required = false) @Email String email,
            @RequestParam(value = "price-currency", required = false) @Size(min = 3, max = 3) String currency,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken,
            @RequestParam(value = "vendor-name") String vendorName,
            @CookieValue(value = "qwep-notification-id", defaultValue = "none") String notificationId) {
        if (multipart.getSize() == 0) {
            log.error("failed request POST /file/register by: name:'{}' file:'{} bearer token:'{}' because multipart size is 0",
                    vendorName, multipart.getName(), bearerToken);
            return ResponseEntity
                    .badRequest()
                    .body(new UniversalResponseDto(false, userTextsConfig.getVendorRegistrationFailed()));
        }
        String accessToken = bearerToken.replaceAll(BEARER, "");
        try {
            FreshVendorMetaDto freshVendorMetaDto = apiQwepService.createVendorUserapi(accessToken, vendorName);
            freshVendorMetaDto = userApiService.addFreshVendorToUserapiAccount(accessToken, freshVendorMetaDto);
            String accountId = freshVendorMetaDto.getAccountId();
            PriceSenderInfoEntity psi = priceSenderService.add(
                    vendorName + " (price)",
                    email,
                    freshVendorMetaDto.getAdminCode(),
                    String.valueOf(freshVendorMetaDto.getVendorId()),
                    freshVendorMetaDto.getViewCode(), currency,
                    Optional.ofNullable(accountId));

            FileSaverThread fileSaverThread = new FileSaverThread(
                    priceFileService,
                    new PriceFile(multipart.getOriginalFilename(), psi, multipart.getBytes())
            );
            PriceFileStream priceFileStream = new PriceFileStream(
                    multipart.getOriginalFilename(),
                    multipart.getInputStream());
            GetAndSavePriceHeadersClassificationAndFile gsphcf = new GetAndSavePriceHeadersClassificationAndFile(
                    bookBuilder,
                    psi.getAdminCode(),
                    priceFileStream,
                    fileSaverThread,
                    priceSenderService
            );
            gsphcf.thread.start();

            log.info("handle request POST /file/register by: name:'{}' file:'{} bearer token:'{}'",
                    vendorName, multipart.getName(), bearerToken);
            return ResponseEntity
                    .ok(new UniversalResponseDto(true, "Price was registered"));
        } catch (Exception exception) {
            log.error("Can't register file with name: {}, email: {}, exceptiion: {}",
                    vendorName, email, ExceptionUtils.getStackTrace(exception));
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new UniversalResponseDto(false, exception.getMessage()));
        }
    }

    @PostMapping(value = "/file/update", consumes = {"multipart/form-data"})
    @Operation(summary = "Обновление зарегистрированного в QWEP прайс-листа.")
    @ApiResponse(responseCode = "200", description = "Обновленный прайс-лист загружен и отправлен на обработку.",
            content = {@Content(mediaType = "text", schema = @Schema(implementation = UniversalResponseDto.class))})
    @ApiResponse(responseCode = "400", description = "Запрос выполнен с ошибками.", content = @Content)
    public ResponseEntity<UniversalResponseDto> updatePriceList(
            @RequestParam("price-list-file") MultipartFile multipart,
            @RequestParam(value = "vendor-email", required = false) @Email String email,
            @RequestHeader("X-Admin-Code") @Size(min = 15, max = 15) String adminCode,
            @RequestParam(value = "price-currency", required = false) @Size(min = 3, max = 3) String currency,
            @CookieValue(value = "qwep-notification-id", defaultValue = "none") String notificationId) {
        log.info("begin to update price from email '{}' adminCode '{}' and qwep-notification-id '{}'",
                email,
                adminCode,
                notificationId);
        Optional<PriceSenderInfoEntity> psiOpt = priceSenderService.getPriceSenderInfoEntity(adminCode);
        if (psiOpt.isPresent() && psiOpt.get().getCurrentState().equals(PriceState.IN_PROGRESS.state))
            return ResponseEntity
                    .badRequest()
                    .body(new UniversalResponseDto(false, IN_PROGRESS_TRY_LATER));
        if (psiOpt.isPresent() && psiOpt.get().getCurrentState().equals(PriceState.AWAITS_CLASSIFICATION.state))
            return ResponseEntity
                    .badRequest()
                    .body(new UniversalResponseDto(false, "Price awaits classification, try later"));
        try {
            PriceFileStream priceFileStream =
                    new PriceFileStream(multipart.getOriginalFilename(), multipart.getInputStream());
            UploadThread uploadThread = new UploadThread(
                    priceConfService,
                    priceFileStream,
                    psiOpt.get(),
                    bookBuilder,
                    priceDataService,
                    priceSenderService,
                    mailSender
            );
            uploadThread.thread.start();
            return ResponseEntity
                    .ok(new UniversalResponseDto(true, "Price starts updating"));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity
                    .badRequest()
                    .body(new UniversalResponseDto(false, IN_PROGRESS_TRY_LATER));
        } catch (IOException exception) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new UniversalResponseDto(false, "Price can't be updated right now"));
        }
    }

    @DeleteMapping("/clean")
    @Operation(summary = "Снятие с публикации прайс-лист(а/ов).")
    @ApiResponse(responseCode = "200", description = "Прайс-лист(ы) снят(ы) с публикации.",
            content = {@Content(mediaType = "text", schema = @Schema(implementation = UniversalResponseDto.class))})
    @ApiResponse(responseCode = "400", description = "Запрос выполнен с ошибками.", content = @Content)
    public ResponseEntity<UniversalResponseDto> handleCleanPriceData(
            @RequestBody List<@Size(min = 15, max = 15) String> adminCodeList) {
        if (!adminCodeList.isEmpty()) {
            adminCodeList
                    .stream()
                    .map(priceSenderService::getPriceTableRef)
                    .forEach(priceDataService::dropPriceTable);
            return ResponseEntity.ok(new UniversalResponseDto(true, userTextsConfig.getVendorRemovalSuccess()));
        } else {
            log.error("suspicious request DELETE /clean by admin-codes: '{}' because empty or invalid body", adminCodeList);
            return ResponseEntity.badRequest().body(new UniversalResponseDto(false, userTextsConfig.getVendorNotFound()));
        }
    }

    @PutMapping("/clean")
    @Operation(summary = "Снятие с публикации прайс-лист(а/ов).")
    @ApiResponse(responseCode = "200", description = "Прайс-лист(ы) снят(ы) с публикации.",
            content = {@Content(mediaType = "text", schema = @Schema(implementation = UniversalResponseDto.class))})
    @ApiResponse(responseCode = "400", description = "Запрос выполнен с ошибками.", content = @Content)
    public ResponseEntity<UniversalResponseDto> handleCleanPriceDataPut(
            @RequestBody List<@Size(min = 15, max = 15) String> adminCodeList) {
        if (!adminCodeList.isEmpty()) {
            adminCodeList
                    .stream()
                    .map(priceSenderService::getPriceTableRef)
                    .forEach(priceDataService::dropPriceTable);
            return ResponseEntity.ok(new UniversalResponseDto(true, userTextsConfig.getVendorRemovalSuccess()));
        } else {
            log.error("suspicious request DELETE /clean by admin-codes: '{}' because empty or invalid body", adminCodeList);
            return ResponseEntity.badRequest().body(new UniversalResponseDto(false, userTextsConfig.getVendorNotFound()));
        }
    }

    @DeleteMapping("/remove")
    @Operation(summary = "Снятие с публикации и УДАЛЕНИЕ прайс-лист(а/ов).")
    @ApiResponse(responseCode = "200", description = "Прайс-лист(ы) снят(ы) с публикации и УДАЛЕН(Ы).",
            content = {@Content(mediaType = "text", schema = @Schema(implementation = UniversalResponseDto.class))})
    @ApiResponse(responseCode = "400", description = "Запрос выполнен с ошибками.", content = @Content)
    public ResponseEntity<UniversalResponseDto> removePriceData(
            @RequestBody List<@Size(min = 15, max = 15) String> adminCodeList,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken) {
        try {
            adminCodeList.forEach(adminCode -> {
                try {
                    UUID priceTableRef = priceSenderService.getPriceTableRef(adminCode);
                    priceDataService.dropPriceTable(priceTableRef);
                } catch (Exception exception) {
                    log.debug("Can't drop priceTAbes for adminCode: {} exception: {}",
                            adminCode, exception.getMessage());
                }
            });
            priceSenderService.removePriceSender(bearerToken, adminCodeList);
            return ResponseEntity.ok(new UniversalResponseDto(true, userTextsConfig.getVendorRemovalSuccess()));
        } catch (Exception exception) {
            log.error("Can't remove prices: {} with cause: {} and trace: {}",
                    adminCodeList, exception.getMessage(), ExceptionUtils.getStackTrace(exception));
            throw exception;
        }
    }

    @PutMapping("/remove")
    @Operation(summary = "Снятие с публикации и УДАЛЕНИЕ прайс-лист(а/ов).")
    @ApiResponse(responseCode = "200", description = "Прайс-лист(ы) снят(ы) с публикации и УДАЛЕН(Ы).",
            content = {@Content(mediaType = "text", schema = @Schema(implementation = UniversalResponseDto.class))})
    @ApiResponse(responseCode = "400", description = "Запрос выполнен с ошибками.", content = @Content)
    public ResponseEntity<UniversalResponseDto> handleRemovePriceDataPut(
            @RequestBody List<@Size(min = 15, max = 15) String> adminCodeList,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken) {
        try {
            adminCodeList.forEach(adminCode -> {
                try {
                    UUID priceTableRef = priceSenderService.getPriceTableRef(adminCode);
                    priceDataService.dropPriceTable(priceTableRef);
                } catch (Exception exception) {
                    log.debug("Can't drop priceTAbes for adminCode: {} exception: {}",
                            adminCode,
                            exception.getMessage());
                }
            });
            priceSenderService.removePriceSender(bearerToken, adminCodeList);
            return ResponseEntity.ok(new UniversalResponseDto(true, userTextsConfig.getVendorRemovalSuccess()));
        } catch (Exception exception) {
            log.error("Can't remove prices: {} with cause: {} and trace: {}",
                    adminCodeList, exception.getMessage(), ExceptionUtils.getStackTrace(exception));
            throw exception;
        }
    }

    @GetMapping("/classification")
    @Operation(summary = "Получение текущей классификации прайс-листа.")
    @ApiResponse(responseCode = "200", description = "Классификация прайс-листа получена.",
            content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ClassificationItem.class))})
    @ApiResponse(responseCode = "400", description = "Запрос выполнен с ошибками.", content = @Content)
    public ResponseEntity<List<ClassificationItem>> getClassificationInfo(
            @RequestHeader("X-Admin-Code") @Size(min = 15, max = 15) String adminCode) {
        Optional<PriceSenderInfoEntity> psiOpt = priceSenderService.getPriceSenderInfoEntity(adminCode);
        if (psiOpt.isPresent()) {
            try {
                List<ClassificationItem> clitems =
                        mapper.readValue(psiOpt.get().getClassificationListJsonString(), new TypeReference<>() {
                        });
                log.info("handle request GET /classification by adminCode: {} and returned {} classification items",
                        adminCode,
                        clitems.size());
                return ResponseEntity.ok(clitems);
            } catch (JsonProcessingException e) {
                log.error("failed to map classification json string to object list with cause: {}",
                        e.getLocalizedMessage());
                return ResponseEntity.badRequest().body(List.of());
            }
        } else {
            log.warn("suspicious request GET /classification by " +
                    "adminCode: {} because price-sender-info entity not found", adminCode);
            return ResponseEntity.ok(List.of());
        }
    }

    @PutMapping(value = "/classification")
    @Operation(summary = "Обновление текущей классификации прайс-листа.")
    @ApiResponse(responseCode = "200", description = "Классификация прайс-листа обновлена.",
            content = {@Content(mediaType = "text", schema = @Schema(implementation = UniversalResponseDto.class))})
    @ApiResponse(responseCode = "400", description = "Запрос выполнен с ошибками.", content = @Content)
    public ResponseEntity<UniversalResponseDto> updateClassification(
            @RequestHeader("X-Admin-Code") @Size(min = 15, max = 15) String adminCode,
            @RequestBody List<ClassificationItem> newClassification) throws RuntimeException {
        Optional<PriceSenderInfoEntity> psiOpt = priceSenderService.getPriceSenderInfoEntity(adminCode);
        if (psiOpt.isEmpty()) {
            log.warn("suspicious request PUT /classification by adminCode: {} " +
                    "because price-sender-info entity not found", adminCode);
            return ResponseEntity
                    .badRequest()
                    .body(new UniversalResponseDto(false, userTextsConfig.getClassificationNotFound()));
        }

        if (!psiOpt.get().getCurrentState().equals(PriceState.AWAITS_CLASSIFICATION.state))
            return ResponseEntity
                    .badRequest()
                    .body(new UniversalResponseDto(false, "Price can't be classified now"));
        try {
            priceSenderService.updateClassification(adminCode, newClassification);
            PriceFileStream priceFileStream = new PriceFileStream(priceFileService.getByPsiId(psiOpt.get().getId()));
            priceFileService.removeByPsiId(psiOpt.get().getId());
            gracefullConfig.setAdminCode(adminCode);
            executorService.submit(new UploadThread(
                    priceConfService,
                    priceFileStream,
                    psiOpt.get(),
                    bookBuilder,
                    priceDataService,
                    priceSenderService,
                    mailSender
            ));
            log.info("handle request PUT /classification by adminCode: {} and returned {} classification items",
                    adminCode,
                    newClassification.size());
            return ResponseEntity
                    .ok(new UniversalResponseDto(true, "Классификация успешно обновлена."));
        } catch (IOException e) {
            log.error("failed to map classification json string to object list with cause: {}",
                    e.getLocalizedMessage());
            return ResponseEntity
                    .badRequest()
                    .body(new UniversalResponseDto(false, userTextsConfig.getClassificationParsingException()));
        }
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Получение таблицы прайс-листов.")
    @ApiResponse(responseCode = "200", description = "Таблица прайс-листов получена.",
            content = {@Content(mediaType = "application/json", schema = @Schema(implementation = PriceInfoDto.class))})
    @ApiResponse(responseCode = "400", description = "Запрос выполнен с ошибками.", content = @Content)
    public ResponseEntity<List<PriceInfoDto>> getDashboard(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken,
            @CookieValue(value = "qwep-notification-id", defaultValue = "none") String notificationId) {
        String accessToken = bearerToken.replaceAll(BEARER, "");
        String quserToken = apiQwepService.getQuserTokenByUaAccessToken(accessToken);
        if (quserToken != null) {
            List<String> vendorIds = apiQwepService.getVendorIdByQUserToken(quserToken);
            List<PriceSenderInfoEntity> priceSenderInfoEntities = priceSenderService.getAllByVendorIdIn(vendorIds);
            HashSet<PriceSenderInfoEntity> priceSet = new HashSet<>(priceSenderInfoEntities);
            log.info("handle request GET /dashboard with bearer token '{}'", bearerToken);
            List<PriceInfoDto> priceInfoDtos = priceSet
                    .stream()
                    .map(psi -> {
                        PriceInfoDto pid = new PriceInfoDto();
                        pid.setLastUpdated(psi.getLastUpdated() != null ? psi.getLastUpdated().toString() : null);
                        pid.setSenderEmail(psi.getEmail());
                        pid.setAdminCode(psi.getAdminCode());
                        pid.setViewCode(psi.getViewCode());
                        pid.setPriceName(psi.getFilePath());
                        pid.setVendorName(psi.getName());
                        pid.setStatus(priceSenderService.getPriceStatus(psi));
                        pid.setSecurityType(apiQwepService.getPriceSecurityStatus(psi));
                        pid.setCurrentState(psi.getCurrentState());
                        pid.setVendorId(psi.getVendorId());
                        return pid;
                    })
                    .toList();

            return ResponseEntity.ok().body(priceInfoDtos);
        } else {
            log.error("failed request GET /dashboard with bearer token '{}' because got null quser token", bearerToken);
            return ResponseEntity
                    .badRequest()
                    .body(List.of());
        }
    }

    @GetMapping("/processing/progress")
    @Operation(summary = "Получение текущего прогресса (0.0 - 1.0) по загрузке/обновлению прайс-листа.")
    @ApiResponse(responseCode = "200", description = "Текущий прогресс по загрузке/обновлению прайс-листа получен.",
            content = {@Content(mediaType = "text", schema = @Schema(implementation = String.class))})
    @ApiResponse(responseCode = "400", description = "Запрос выполнен с ошибками.", content = @Content)
    public ResponseEntity<String> handleGetPriceUpdateProgress(
            @RequestHeader("X-Admin-Code") @Size(min = 15, max = 15) String adminCode) {
        log.debug("handle request GET /price/update/progress with admin-code '{}'", adminCode);
        return ResponseEntity.ok(core.getPriceUpdateProgress(adminCode));
    }

    @PostMapping("/basket/add")
    @Operation(summary = " Добавление позиции из прайса в корзину.")
    @ApiResponse(responseCode = "200", description = "Заявка оформлена, уведомление отправлено.",
            content = {@Content(mediaType = "application/json", schema = @Schema(implementation = UniversalResponseDto.class))})
    @ApiResponse(responseCode = "400", description = "Запрос выполнен с ошибками.", content = @Content)
    public ResponseEntity<UniversalResponseDto> handleAddBasketItem(
            @RequestBody List<BasketAddItemDto> basketItems,
            @RequestHeader("X-Vendor-Id") UUID vendorId) {
        if (basketItems.size() < 100) {
            basketItems.forEach(basketAddItemDto -> Arrays.stream(basketAddItemDto.getRefTable())
                    .forEach(tableName -> {
                        List<PriceDto> items = searchService.fetchPriceItems(basketAddItemDto.getItemId(), tableName);
                        items.forEach(item -> {
                            Optional<String> vendorEmail = apiQwepService.getPromoEmailFromApiQwep(vendorId);
                            Optional<PriceSenderInfoEntity> psiOpt = priceSenderService
                                    .getPriceSenderInfoEntity(vendorId);
                            AtomicReference<String> vendorName = new AtomicReference<>("Поставщик не найден");
                            psiOpt.ifPresent(psi -> vendorName.set(psi.getName()));
                            try {
                                mainFunctionService.sendBasketAddMessage(
                                        item,
                                        basketAddItemDto,
                                        vendorEmail.orElse("Email не найден"), vendorName.get());
                            } catch (MessagingException e) {
                                throw new MailSendException(
                                        String.format(userTextsConfig.getUserBasketAddNotificationFailed(),
                                                vendorEmail.orElse("null")));
                            }
                        });
                    }));
        } else {
            throw new IllegalArgumentException(userTextsConfig.getBasketSizeLimitReached());
        }
        return ResponseEntity.ok(new UniversalResponseDto(true, userTextsConfig.getBasketOrderCreatedOk()));
    }

    @GetMapping("/notifications")
    @Operation(summary = "Получение уведомлений о работе сервиса.")
    @ApiResponse(responseCode = "200", description = "Уведомления получены.",
            content = {@Content(mediaType = "application/json", schema = @Schema(implementation = String.class))})
    @ApiResponse(responseCode = "400", description = "Запрос выполнен с ошибками.", content = @Content)
    public ResponseEntity<List<String>> handleGetServiceNotifications(
            @CookieValue(value = "qwep-notification-id", defaultValue = "none") String notificationId) {
        List<String> notificationList = notificationService.getServiceNotificationsById(notificationId);
        log.info("will return some notifications by qwep-notification-id '{}' ; '{}'",
                notificationId, notificationList);
        return ResponseEntity.ok(notificationList);
    }

    @PostMapping(value = "/preview/update", consumes = {"multipart/form-data"})
    @Operation(summary = "Получение первых 10 позиций прайса без записи в базу при обновлении прайса.")
    @ApiResponse(responseCode = "200", description = "Получены первые 10 позиций из прайса.",
            content = {@Content(mediaType = "text", schema = @Schema(implementation = PriceDto[].class))})
    @ApiResponse(responseCode = "400", description = "Запрос выполнен с ошибками.", content = @Content)
    public ResponseEntity<Object> getPreviewUpdate(
            @RequestParam(value = "price-list-file") MultipartFile multipart,
            @RequestParam(value = "vendor-email", required = false) @Email String email,
            @RequestHeader("X-Admin-Code") @Size(min = 15, max = 15) String adminCode,
            @RequestParam(value = "price-currency", required = false) @Size(min = 3, max = 3) String currency) {
        try {
            if (priceSenderService
                    .getPriceSenderInfoEntity(adminCode)
                    .get()
                    .getCurrentState()
                    .equals(PriceState.IN_PROGRESS.state)) {
                return ResponseEntity
                        .badRequest()
                        .body(new UniversalResponseDto(false, IN_PROGRESS_TRY_LATER));
            }
            Optional<PriceSenderInfoEntity> psiOpt = priceSenderService.getPriceSenderInfoEntity(adminCode);
            PriceFileStream priceFileStream = new PriceFileStream(
                    multipart.getOriginalFilename(),
                    multipart.getInputStream());
            Book book = bookBuilder.build(priceFileStream);
            List<PriceDto> priceDtos = book.getPriceDtos(
                    List.of(mapper
                            .readValue(psiOpt.get().getClassificationListJsonString(), ClassificationItem[].class)),
                    priceConfService.buildConf(psiOpt.get().getConfigurationsJsonString()),
                    psiOpt.get().getPriceCurrency()
            );
            if (priceDtos.size() >= 11) return ResponseEntity.ok(priceDtos.subList(0, 10));
            else return ResponseEntity.ok(priceDtos);
        } catch (NoSuchElementException exception) {
            log.info("Tried to get nonexistent priceSender: {} ", ExceptionUtils.getStackTrace(exception));
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new Response<>(
                            false,
                            "Can't get priceSender:" + exception.getMessage()));
        } catch (PriceProcessingException | MessagingException | IOException exception) {
            log.error("Can't make preview: {}", exception);
            return ResponseEntity.badRequest().body("Can't make preview beacuse of " + exception.getMessage());
        }
    }

    @PostMapping(value = "/preview/register", consumes = {"multipart/form-data"})
    @Operation(summary = "Получение первых 10 позиций прайса без записи в базу сразу после регистрации файла.")
    @ApiResponse(responseCode = "200", description = "Получены первые 10 позиций из прайса.",
            content = {@Content(mediaType = "text", schema = @Schema(implementation = PriceDto[].class))})
    @ApiResponse(responseCode = "400", description = "Запрос выполнен с ошибками.", content = @Content)
    public ResponseEntity<Object> getPreviewRegister(
            @RequestHeader("X-Admin-Code") @Size(min = 15, max = 15) String adminCode,
            @RequestParam(value = "classification") String classification,
            @RequestParam(value = "price-currency", required = false) @Size(min = 3, max = 3) String currency) {
        try {
            if (priceSenderService
                    .getPriceSenderInfoEntity(adminCode)
                    .get()
                    .getCurrentState()
                    .equals(PriceState.IN_PROGRESS.state))
                return ResponseEntity
                        .badRequest()
                        .body(new UniversalResponseDto(false, IN_PROGRESS_TRY_LATER));
            Optional<PriceSenderInfoEntity> psiOpt = priceSenderService.getPriceSenderInfoEntity(adminCode);
            PriceFileStream priceFileStream = new PriceFileStream(psiOpt.get().getPriceFile());
            Book book = bookBuilder.build(priceFileStream);
            List<PriceDto> priceDtos = book.getPriceDtos(
                    List.of(mapper.readValue(classification, ClassificationItem[].class)),
                    priceConfService.buildConf(psiOpt.get().getConfigurationsJsonString()),
                    psiOpt.get().getPriceCurrency()
            );
            if (priceDtos.size() >= 11) return ResponseEntity.ok(priceDtos.subList(0, 10));
            else return ResponseEntity.ok(priceDtos);
        } catch (NoSuchElementException exception) {
            log.info("Tried to get nonexistent priceSender: {} ", ExceptionUtils.getStackTrace(exception));
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new Response<>(
                            false,
                            "Can't get priceSender:" + exception.getMessage()));
        } catch (PriceProcessingException | MessagingException | IOException exception) {
            return ResponseEntity.badRequest().body("Can't build preview with exception: " + exception);
        }
    }

    @GetMapping("/priceSenderInfo/{adminCode}")
    @Operation(summary = "Получение прайса по админ коду.")
    @ApiResponse(responseCode = "200", description = "Получен прайс.",
            content = {@Content(mediaType = "application/json", schema = @Schema(implementation = PriceInfoDto.class))})
    @ApiResponse(responseCode = "400", description = "Запрос выполнен с ошибками.", content = @Content)
    public ResponseEntity<PriceInfoDto> getPriceSenderInfoByAdminCode(@PathVariable String adminCode) {
        Optional<PriceSenderInfoEntity> optionalPsi = priceSenderService.getPriceSenderInfoEntity(adminCode);
        if (optionalPsi.isEmpty())
            return ResponseEntity.badRequest().body(null);
        PriceSenderInfoEntity psi = optionalPsi.get();
        PriceInfoDto priceInfoDto = new PriceInfoDto(
                psi,
                apiQwepService.getPriceSecurityStatus(psi),
                priceSenderService.getPriceStatus(psi));
        return ResponseEntity.ok(priceInfoDto);
    }

    @PutMapping("/configuration")
    @Operation(summary = "Загрузка конфигурации для прайса")
    @ApiResponse(responseCode = "200", description = "Конфигурация прайса успено загружена",
            content = {@Content(mediaType = "text", schema = @Schema(implementation = UniversalResponseDto.class))})
    @ApiResponse(responseCode = "400", description = "Запрос выполнен с ошибками.", content = @Content)
    public ResponseEntity<UniversalResponseDto> updateConfiguration(@RequestHeader("X-Admin-Code")
                                                                    @Size(min = 15, max = 15) String adminCode,
                                                                    @RequestBody List<Configuration> configurations) {
        Optional<PriceSenderInfoEntity> psiOpt = priceSenderService.getPriceSenderInfoEntity(adminCode);
        if (psiOpt.isEmpty()) {
            log.warn(
                    "suspicious request PUT /configuration by adminCode: {} because price-sender-info entity not found",
                    adminCode);
            return ResponseEntity
                    .badRequest()
                    .body(new UniversalResponseDto(false, userTextsConfig.getClassificationNotFound()));
        }
        if (psiOpt.get().getCurrentState().equals(PriceState.IN_PROGRESS.state)) {
            return ResponseEntity
                    .badRequest()
                    .body(new UniversalResponseDto(false, IN_PROGRESS_TRY_LATER));
        }
        try {
            priceConfService.checkConfigurations(configurations);
            psiOpt.get().setConfigurationsJsonString(
                    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(configurations));
            priceSenderService.savePriceSenderInfoEntity(psiOpt);
            log.info("handle request PUT /configuration by adminCode: {}", adminCode);

            configurations
                    .parallelStream()
                    .filter(c -> c.getType().equals(ConfigurationType.CurrencyConversion)
                            && !psiOpt.get().getCurrentState().equals(PriceState.AWAITS_CLASSIFICATION.state))
                    .findFirst()
                    .ifPresent(c -> {
                        psiOpt.get().setCurrentState(PriceState.IN_PROGRESS.state);
                        priceSenderService.savePriceSenderInfoEntity(psiOpt);
                        CurrencyConversionRunnable currencyConversionRunnable = new CurrencyConversionRunnable(
                                priceSenderService,
                                priceDataService,
                                currencyConversionService,
                                c,
                                psiOpt.get());
                        currencyConversionRunnable.thread.start();
                    });

            return ResponseEntity.ok(
                    new UniversalResponseDto(true, "Конфигурация успешно обновлена.")
            );
        } catch (IllegalArgumentException exception) {
            log.info("Tried to put wrong configuration: {}", exception.getLocalizedMessage());
            return ResponseEntity
                    .badRequest()
                    .body(
                            new UniversalResponseDto(
                                    false,
                                    "Wrong configuration: " + exception.getMessage()
                            )
                    );
        } catch (JsonProcessingException e) {
            log.error(
                    "failed to map configuration json string to object list with cause: {}",
                    e.getLocalizedMessage()
            );
            return ResponseEntity
                    .badRequest()
                    .body(
                            new UniversalResponseDto(
                                    false,
                                    "failed to map configuration json string to object"
                            )
                    );
        }
    }

    @GetMapping("/configuration")
    @Operation(summary = "Получение текущей конфигурации прайс-листа.")
    @ApiResponse(responseCode = "200", description = "Конфигурация прайс-листа получена.",
            content = {@Content(mediaType = "application/json", schema = @Schema(implementation = Configuration[].class))})
    @ApiResponse(responseCode = "400", description = "Запрос выполнен с ошибками.", content = @Content)
    public ResponseEntity<List<Configuration>> getConfuguration(
            @RequestHeader("X-Admin-Code")
            @Size(min = 15, max = 15) String adminCode
    ) {
        Optional<PriceSenderInfoEntity> psiOpt = priceSenderService.getPriceSenderInfoEntity(adminCode);
        if (psiOpt.isPresent()) {
            try {
                List<Configuration> configurations = psiOpt.get().getConfigurationsJsonString() != null ?
                        List.of(mapper.readValue(
                                psiOpt.get().getConfigurationsJsonString(),
                                Configuration[].class
                        )) :
                        List.of();

                log.info(
                        "handle request GET /configuration by adminCode: {} and returned {} configuration items",
                        adminCode,
                        configurations.size()
                );
                return ResponseEntity.ok(configurations);
            } catch (JsonProcessingException e) {
                log.error("failed to map configuration json string to object list with cause: {}",
                        e.getLocalizedMessage());
                return ResponseEntity
                        .badRequest()
                        .body(List.of());
            }
        } else {
            log.warn(
                    "suspicious request GET /configuration by adminCode: " +
                            "{} because price-sender-info entity not found",
                    adminCode
            );
            return ResponseEntity.ok(List.of());
        }
    }
}
