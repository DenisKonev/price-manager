package org.qwep.qweppricemanager.pricesender;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.qwep.qweppricemanager.UploadThread;
import org.qwep.qweppricemanager.commons.UserTextsConfig;
import org.qwep.qweppricemanager.external.ApiQwepService;
import org.qwep.qweppricemanager.external.Create1CVendorDTO;
import org.qwep.qweppricemanager.external.FreshVendorMetaDto;
import org.qwep.qweppricemanager.external.UserApiService;
import org.qwep.qweppricemanager.mail.MailSender;
import org.qwep.qweppricemanager.pricedata.PriceDataService;
import org.qwep.qweppricemanager.pricedata.PriceFileStream;
import org.qwep.qweppricemanager.pricedata.fileconverter.BookBuilder;
import org.qwep.qweppricemanager.pricefile.PriceFileService;
import org.qwep.qweppricemanager.pricesender.configuration.Configuration;
import org.qwep.qweppricemanager.pricesender.configuration.PriceConfService;
import org.qwep.qweppricemanager.pricesender.enums.PriceState;
import org.qwep.qweppricemanager.rest.dto.*;
import org.springdoc.api.ErrorMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/pricesender")
public class PriceSenderController {
    private final PriceSenderService priceSenderService;
    private final ApiQwepService apiQwepService;
    private final PriceConfService priceConfService;
    private final ObjectMapper mapper;
    private final UserApiService userApiService;
    private final UserTextsConfig userTextsConfig;
    private final PriceFileService priceFileService;
    private final PriceDataService priceDataService;
    private final BookBuilder bookBuilder;
    private final MailSender mailSender;

    @Autowired
    public PriceSenderController(PriceSenderService priceSenderService,
                                 ApiQwepService apiQwepService,
                                 PriceConfService priceConfService,
                                 UserApiService userApiService,
                                 UserTextsConfig userTextsConfig,
                                 PriceFileService priceFileService,
                                 PriceDataService priceDataService,
                                 BookBuilder bookBuilder,
                                 MailSender mailSender) {
        this.priceSenderService = priceSenderService;
        this.apiQwepService = apiQwepService;
        this.priceConfService = priceConfService;
        this.userApiService = userApiService;
        this.userTextsConfig = userTextsConfig;
        this.priceFileService = priceFileService;
        this.priceDataService = priceDataService;
        this.bookBuilder = bookBuilder;
        this.mailSender = mailSender;
        this.mapper = new ObjectMapper();
    }

    @Operation(summary = "Получить клиента по админ коду.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Получен клиент.",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Response.class))}),
            @ApiResponse(responseCode = "400", description = "Нет такого клиента.",
                    content = @Content),
            @ApiResponse(responseCode = "500", description = "Запрос выполнен с ошибками.",
                    content = @Content)})
    @GetMapping(value = "/getByAdminCode/{adminCode}")
    public ResponseEntity<Response<PriceSenderInfoEntity>> getByAdminCode(@PathVariable String adminCode) {
        return ResponseEntity
                .ok()
                .body(
                        new Response<>(priceSenderService.getPriceSenderInfoEntity(adminCode).orElseThrow(),
                                true,
                                "Got priceSender")
                );

    }

    @Operation(summary = "Получить клиента по vendorId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Получен клиент.",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Response.class))}),
            @ApiResponse(responseCode = "400", description = "Нет такого клиента.",
                    content = @Content),
            @ApiResponse(responseCode = "500", description = "Запрос выполнен с ошибками.",
                    content = @Content)})
    @GetMapping(value = "/getByVendorId/{vendorId}")
    public ResponseEntity<Response<PriceSenderInfoEntity>> getByVendorId(@PathVariable String vendorId) {
        return ResponseEntity
                .ok()
                .body(
                        new Response<>(priceSenderService.getPriceSenderInfoEntity(UUID.fromString(vendorId))
                                .orElseThrow(),
                                true,
                                "Got priceSender")
                );
    }

    @Operation(summary = "Создать клиента для 1c.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Создан клиент.",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Response.class))}),
            @ApiResponse(responseCode = "400", description = "Нет такого клиента.",
                    content = @Content),
            @ApiResponse(responseCode = "500", description = "Запрос выполнен с ошибками.",
                    content = @Content)})
    @PostMapping(value = "/add1C", consumes = {"application/json"})
    public ResponseEntity<Response<FreshVendorMetaDto>> add1C(@RequestBody Add1CPriceDTO add1CPriceDTO)
            throws Exception {
        Validate.notBlank(add1CPriceDTO.getVendorName());
        Validate.notBlank(add1CPriceDTO.getVendorMail());
        FreshVendorMetaDto freshVendorMetaDto =
                apiQwepService.createVendor1C(new Create1CVendorDTO(add1CPriceDTO));


        priceSenderService.add(
                add1CPriceDTO.getVendorName() + " (price)",
                add1CPriceDTO.getVendorMail(),
                freshVendorMetaDto.getAdminCode(),
                String.valueOf(freshVendorMetaDto.getVendorId()),
                freshVendorMetaDto.getViewCode(),
                add1CPriceDTO.getCurrency(),
                Optional.empty()
        );

        log.info("Added price sender with {}", freshVendorMetaDto);
        return ResponseEntity
                .ok(new Response<>(freshVendorMetaDto, true, "PriceSender added"));
    }


    @Operation(summary = "Создать клиента для юзерапи.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Создан клиент.",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Response.class))}),
            @ApiResponse(responseCode = "400", description = "Нет такого клиента.",
                    content = @Content),
            @ApiResponse(responseCode = "500", description = "Запрос выполнен с ошибками.",
                    content = @Content)})
    @PostMapping(value = "/add", consumes = {"application/json"})
    public ResponseEntity<Response<FreshVendorMetaDto>> add(@RequestBody @Validated AddPriceDto addPriceDto)
            throws Exception {
        FreshVendorMetaDto freshVendorMetaDto =
                apiQwepService.createVendorUserapi(addPriceDto.getAccessToken(), addPriceDto.getName());


        Optional<String> accountId = Optional.empty();
        if (addPriceDto.getAccessToken() != null) {
            freshVendorMetaDto =
                    userApiService.addFreshVendorToUserapiAccount(addPriceDto.getAccessToken(), freshVendorMetaDto);
            if (freshVendorMetaDto.getAccountId() != null)
                accountId = Optional.of(freshVendorMetaDto.getAccountId());
        }
        priceSenderService.add(
                addPriceDto.getName(),
                addPriceDto.getEmail(),
                freshVendorMetaDto.getAdminCode(),
                String.valueOf(freshVendorMetaDto.getVendorId()),
                freshVendorMetaDto.getViewCode(),
                addPriceDto.getCurrency(),
                accountId
        );

        log.info("Added price sender with {}", freshVendorMetaDto);
        return ResponseEntity
                .ok(new Response<>(freshVendorMetaDto, true, "PriceSender added"));
    }

    @Operation(summary = "Удалить клиента.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Клиент удален.",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Response.class))}),
            @ApiResponse(responseCode = "500", description = "Запрос выполнен с ошибками.",
                    content = @Content)})
    @DeleteMapping(value = "/remove/{adminCode}/{accessToken}")
    public ResponseEntity<Response<Object>> delete(@PathVariable String adminCode,
                                                   @PathVariable String accessToken) {

        PriceSenderInfoEntity psi = priceSenderService.getPriceSenderInfoEntity(adminCode).get();
        if (psi.getUserApiAccountId() != null
                && !psi.getUserApiAccountId().isEmpty()) {
            userApiService.disableVendorUserapiAccount(psi.getUserApiAccountId(), accessToken);
        }
        priceSenderService.removePriceSender(adminCode);
        log.info("Removed price sender with admincode: {} and accessToken: {}", adminCode, accessToken);
        return ResponseEntity
                .ok(new Response<>(true, "PriceSender removed"));

    }

    @Operation(summary = "Удалить клиента.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Клиент удален.",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Response.class))}),
            @ApiResponse(responseCode = "500", description = "Запрос выполнен с ошибками.",
                    content = @Content)})
    @DeleteMapping(value = "/remove/{adminCode}")
    public ResponseEntity<Response<Object>> delete(@PathVariable String adminCode) {
        priceSenderService.removePriceSender(adminCode);
        log.info("Removed price sender with adminCode: {}", adminCode);
        return ResponseEntity
                .ok(new Response<>(true, "PriceSender removed"));
    }

    @PutMapping("/configuration")
    @Operation(summary = "Загрузка конфигурации для прайса")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Конфигурация прайса успено загружена",
                    content = {@Content(mediaType = "text",
                            schema = @Schema(implementation = UniversalResponseDto.class))}),
            @ApiResponse(responseCode = "400", description = "Запрос выполнен с ошибками.",
                    content = @Content)})
    public ResponseEntity<Response<Object>> updateConfiguration(@RequestHeader("X-Admin-Code")
                                                                @Size(min = 15, max = 15) String adminCode,
                                                                @RequestBody List<Configuration> configurations)
            throws JsonProcessingException {
        Optional<PriceSenderInfoEntity> psiOpt = priceSenderService.getPriceSenderInfoEntity(adminCode);
        priceConfService.checkConfigurations(configurations);
        psiOpt.get().setConfigurationsJsonString(
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(configurations)
        );
        priceSenderService.savePriceSenderInfoEntity(psiOpt);
        log.info("handle request PUT /configuration by adminCode: {} and conf size: {}",
                adminCode, configurations.size());
        return ResponseEntity.ok(
                new Response<>(true, "Conf was updated")
        );

    }

    @GetMapping("/configuration")
    @Operation(summary = "Получение текущей конфигурации прайс-листа.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Конфигурация прайс-листа получена.",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Configuration[].class))}),
            @ApiResponse(responseCode = "400", description = "Запрос выполнен с ошибками.",
                    content = @Content)})
    public ResponseEntity<Response<List<Configuration>>> getConfuguration(
            @RequestHeader("X-Admin-Code")
            @Size(min = 15, max = 15) String adminCode
    ) throws JsonProcessingException {
        Optional<PriceSenderInfoEntity> psiOpt = priceSenderService.getPriceSenderInfoEntity(adminCode);

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
        return ResponseEntity.ok(new Response<>(configurations, true, "Got confs"));

    }

    @Operation(summary = "Получить состояние прайса по админ коду.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Получено состояние.",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Response.class))}),
            @ApiResponse(responseCode = "400", description = "Нет такого клиента.",
                    content = @Content),
            @ApiResponse(responseCode = "500", description = "Запрос выполнен с ошибками.",
                    content = @Content)})
    @GetMapping(value = "/getState/{adminCode}")
    public ResponseEntity<Response<String>> getStateByAdminCode(@PathVariable String adminCode) {

        return ResponseEntity
                .ok()
                .body(
                        new Response<>(priceSenderService.getState(adminCode),
                                true,
                                "Got priceSender's state")
                );

    }


    @PutMapping("/classification")
    @Operation(summary = "Обновление текущей классификации прайс-листа ДЛЯ АДМИНКИ, НЕ ДЛЯ КЛИЕНТОВ .")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Классификация прайс-листа обновлена.",
                    content = {@Content(mediaType = "text",
                            schema = @Schema(implementation = UniversalResponseDto.class))}),
            @ApiResponse(responseCode = "400", description = "Запрос выполнен с ошибками.",
                    content = @Content)})
    public ResponseEntity<UniversalResponseDto> updateClassification(
            @RequestHeader("X-Admin-Code") @Size(min = 15, max = 15) String adminCode,
            @RequestBody List<ClassificationItem> newClassification) throws IOException {
        Optional<PriceSenderInfoEntity> psiOpt = priceSenderService.getPriceSenderInfoEntity(adminCode);
        if (psiOpt.isEmpty()) {
            log.warn("suspicious request PUT /classification by adminCode: {} because price-sender-info entity not found",
                    adminCode);
            return ResponseEntity
                    .badRequest()
                    .body(new UniversalResponseDto(false, userTextsConfig.getClassificationNotFound()));
        }

        if (psiOpt.get().getCurrentState().equals(PriceState.IN_PROGRESS.state))
            return ResponseEntity
                    .badRequest()
                    .body(new UniversalResponseDto(false, "Price is in progress, try later"));

        priceSenderService.updateClassification(adminCode, newClassification);
        if (psiOpt.get().getCurrentState().equals(PriceState.AWAITS_CLASSIFICATION.state)) {
            PriceFileStream priceFileStream = new PriceFileStream(priceFileService.getByPsiId(psiOpt.get().getId()));
            priceFileService.removeByPsiId(psiOpt.get().getId());
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
        }
        log.info("handle request PUT /classification by adminCode: {} and returned {} classification items",
                adminCode, newClassification.size());
        return ResponseEntity
                .ok(new UniversalResponseDto(true, "Классификация успешно обновлена."));


    }

    @PostMapping("/email/update")
    @Operation(summary = "Изменить почту для получения уведомлений.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Почта изменена."),
            @ApiResponse(responseCode = "400", description = "Запрос выполнен с ошибками.")
    })
    public ResponseEntity<Response<Object>> updateClassification(@RequestBody @Validated ChangeEmailDto changeEmailDto) {
        priceSenderService.changeEmail(changeEmailDto.getAdminCode(), changeEmailDto.getEmail());
        return ResponseEntity
                .ok(new Response<>(true, "Почтовый адрес изменен."));
    }

    @PutMapping("/email/identification")
    @Operation(summary = "Зафиксировать почту как способ идентификации прайса",
            description = "Зафиксировать почту как способ идентификации прайса для работы с почтой. " +
                    "Если почтовый адрес привязан к любому другому прайсу, то операция закончится ошибкой. " +
                    "Если почты зафиксирована успешно, то создание другого прайса с этой же почтой закончится ошибкой.")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Почта зафиксирована."
            ),
            @ApiResponse(responseCode = "409",
                    description = "Почта уже используется.",
                    content = {
                            @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = ErrorMessage.class))
                    }),
            @ApiResponse(responseCode = "404",
                    description = "Прайс не был найден.",
                    content = {
                            @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = ErrorMessage.class))
                    })
    })
    public ResponseEntity<Void> setEmailIdentification(@RequestBody EmailIdentificationDTO emailIdentificationDTO) {
        log.info("Setting {} to use email as identification mark for adminCode: {} ",
                emailIdentificationDTO.isEmailIdentification(), emailIdentificationDTO.adminCode());
        priceSenderService.setEmailIdentification(
                emailIdentificationDTO.adminCode(),
                emailIdentificationDTO.isEmailIdentification()
        );
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Изменение кол-ва в позиции в прайсе.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Количество в позиции изменено.",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Response.class))}),
            @ApiResponse(responseCode = "400", description = "Запрос выполнен с ошибками.",
                    content = @Content),
            @ApiResponse(responseCode = "500", description = "Ошибка сервера.",
                    content = @Content)})
    @PostMapping("/changeQuantityItemsInPrice")
    public ResponseEntity<Response<String>> changeCountItems(
            @RequestBody @Valid List<ChangeQuantityItemDto> cngQuantityItem) {
        log.debug("Receive request on change Item quantity");
        try {
            priceSenderService.changeQuantity(cngQuantityItem);
            return ResponseEntity.ok(new Response<>(true, "item quantity was changed"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new Response<>(false, e.getMessage()));
        } catch (Exception e) {
            log.error("failed to change quantity Item : {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(new Response<>(false, e.getMessage()));
        }
    }


}