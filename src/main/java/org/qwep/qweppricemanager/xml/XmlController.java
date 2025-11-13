package org.qwep.qweppricemanager.xml;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.apache.xmlbeans.ThreadLocalUtil;
import org.qwep.qweppricemanager.external.ApiQwepService;
import org.qwep.qweppricemanager.external.FreshVendorMetaDto;
import org.qwep.qweppricemanager.external.UserApiService;
import org.qwep.qweppricemanager.pricedata.PriceDataService;
import org.qwep.qweppricemanager.pricesender.ClassificationItem;
import org.qwep.qweppricemanager.pricesender.PriceSenderInfoEntity;
import org.qwep.qweppricemanager.pricesender.PriceSenderService;
import org.qwep.qweppricemanager.pricesender.configuration.Configuration;
import org.qwep.qweppricemanager.pricesender.configuration.PriceConfService;
import org.qwep.qweppricemanager.rest.dto.UniversalResponseDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.qwep.qweppricemanager.pricesender.configuration.ConfigurationType.Offsting;
import static org.qwep.qweppricemanager.pricesender.enums.PriceState.*;
import static org.qwep.qweppricemanager.rest.PriceDataV3Controller.IN_PROGRESS_TRY_LATER;

@Slf4j
@Validated
@RestController
@RequestMapping("/xml")
public class XmlController {

    public static final String BEARER = "Bearer || ";

    private final ObjectMapper objectMapper;
    private final ApiQwepService apiQwepService;
    private final UserApiService userApiService;
    private final PriceDataService priceDataService;
    private final PriceConfService priceConfService;
    private final PriceSenderService priceSenderService;
    private final XmlDataProcessorService xmlDataProcessorService;
    private final RedisTemplate<String, String> redisTemplate;

    public XmlController(ObjectMapper objectMapper,
                         ApiQwepService apiQwepService,
                         UserApiService userApiService,
                         PriceDataService priceDataService,
                         PriceConfService priceConfService,
                         PriceSenderService priceSenderService,
                         XmlDataProcessorService xmlDataProcessorService,
                         @Qualifier("redis.internal") RedisTemplate<String, String> redisTemplate) {
        this.objectMapper = objectMapper;
        this.apiQwepService = apiQwepService;
        this.userApiService = userApiService;
        this.priceDataService = priceDataService;
        this.priceConfService = priceConfService;
        this.priceSenderService = priceSenderService;
        this.xmlDataProcessorService = xmlDataProcessorService;
        this.redisTemplate = redisTemplate;
    }

    @PostMapping(value = "/register")
    @Operation(summary = "Регистрация нового прайс-листа в формате XML-ссылки.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Новый прайс-лист зарегистрирован в QWEP и ожидает классификацию."),
            @ApiResponse(responseCode = "400", description = "Запрос выполнен с ошибками.")})
    public ResponseEntity<Void> register(@RequestBody RegistrationDto registrationDto,
                                         @RequestHeader("X-auth") String bearerToken) {
        if (registrationDto.link().isEmpty()) {
            log.error("link is empty");
            return ResponseEntity.badRequest().build();
        }

        String accessToken = bearerToken.replaceAll(BEARER, "");
        try {
            FreshVendorMetaDto freshVendorMetaDto = apiQwepService.createVendorUserapi(accessToken, registrationDto.vendorName());
            freshVendorMetaDto = userApiService.addFreshVendorToUserapiAccount(accessToken, freshVendorMetaDto);
            String accountId = freshVendorMetaDto.getAccountId();

            PriceSenderInfoEntity psi = new PriceSenderInfoEntity();
            psi.setFilePath(registrationDto.link());
            psi.setConfigurationsJsonString(
                    objectMapper.writeValueAsString(List.of(
                            new Configuration(Offsting, registrationDto.offsting(), ""))));
            psi.setName(registrationDto.vendorName() + " (price)");
            psi.setEmail(registrationDto.vendorEmail());
            psi.setAdminCode(freshVendorMetaDto.getAdminCode());
            psi.setClassificationListJsonString("[ ]");
            psi.setVendorId(String.valueOf(freshVendorMetaDto.getVendorId()));
            psi.setPriceTableRefs(new String[]{});
            psi.setViewCode(freshVendorMetaDto.getViewCode());
            psi.setPriceCurrency(registrationDto.currency());
            psi.setUserApiAccountId(accountId);
            psi.setCurrentState(REGISTERED.state);
            psi.setEmailIdentification(false);
            priceSenderService.savePriceSenderInfoEntity(psi);
            log.info("Added price sender with {}", freshVendorMetaDto);

            ParseXmlHeadersAndSave pxhas = new ParseXmlHeadersAndSave(
                    registrationDto.link(),
                    psi,
                    priceSenderService,
                    xmlDataProcessorService);
            pxhas.thread.start();
        } catch (Exception e) {
            log.info("registration failed in xml controller: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } finally {
            ThreadLocalUtil.clearAllThreadLocals();
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/update")
    @Operation(summary = "Обновление зарегистрированного в QWEP прайс-листа.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Обновленный прайс-лист загружен и отправлен на обработку."),
            @ApiResponse(responseCode = "400", description = "Запрос выполнен с ошибками.")})
    public UniversalResponseDto update(
            @RequestHeader("X-Admin-Code") @Size(min = 15, max = 15) String adminCode) {
        log.info("begin to update price from adminCode '{}'", adminCode);
        Optional<PriceSenderInfoEntity> psiOpt = priceSenderService.getPriceSenderInfoEntity(adminCode);
        if (psiOpt.isPresent() && psiOpt.get().getCurrentState().equals(IN_PROGRESS.state)) {
            return new UniversalResponseDto(false, IN_PROGRESS_TRY_LATER);
        }
        if (psiOpt.isPresent() && psiOpt.get().getCurrentState().equals(AWAITS_CLASSIFICATION.state)) {
            return new UniversalResponseDto(false, "Price awaits classification, try later");
        }
        try {
            List<ClassificationItem> getClassification =
                    List.of(objectMapper.readValue(psiOpt.get().getClassificationListJsonString(), ClassificationItem[].class));
            SaveNewClassificationAndUpdatePriceData sncapp =
                    new SaveNewClassificationAndUpdatePriceData(
                            adminCode,
                            priceSenderService,
                            psiOpt.get(),
                            priceConfService,
                            priceDataService,
                            getClassification,
                            xmlDataProcessorService
                    );
            sncapp.thread.start();
            return new UniversalResponseDto(true, "Price starts updating");
        } catch (IllegalArgumentException exception) {
            return new UniversalResponseDto(false, IN_PROGRESS_TRY_LATER);
        } catch (IOException exception) {
            return new UniversalResponseDto(false, "Price can't be updated right now");
        } finally {
            ThreadLocalUtil.clearAllThreadLocals();
        }
    }

    @PostMapping("/classification")
    @Operation(summary = "Получение текущей классификации прайс-листа.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Классификация прайс-листа получена."),
            @ApiResponse(responseCode = "400", description = "Запрос выполнен с ошибками.")})
    public ResponseEntity<Void> setClassification(
            @RequestHeader("X-Admin-Code") @Size(min = 15, max = 15) String adminCode,
            @RequestBody List<ClassificationItem> newClassification) {
        Optional<PriceSenderInfoEntity> psiOpt = priceSenderService.getPriceSenderInfoEntity(adminCode);
        if (psiOpt.isPresent() && psiOpt.get().getCurrentState().equals(IN_PROGRESS.state)) {
            log.info(IN_PROGRESS_TRY_LATER);
            return ResponseEntity.badRequest().build();
        }

        if (!psiOpt.get().getCurrentState().equals(AWAITS_CLASSIFICATION.state)) {
            log.info("Tried to classify price with state: {} and amdinCode: {}",
                    psiOpt.get().getCurrentState(), psiOpt.get().getAdminCode());
            return ResponseEntity.badRequest().build();
        }
        try {
            priceSenderService.updateClassification(adminCode, newClassification);
            SaveNewClassificationAndUpdatePriceData getClassificationAndSave =
                    new SaveNewClassificationAndUpdatePriceData(
                            adminCode,
                            priceSenderService,
                            psiOpt.get(),
                            priceConfService,
                            priceDataService,
                            newClassification,
                            xmlDataProcessorService
                    );
            getClassificationAndSave.thread.start();
        } catch (Exception exception) {
            log.info("exception: {}, class: {}", exception.getMessage(), this.getClass());
            return ResponseEntity.badRequest().build();
        } finally {
            ThreadLocalUtil.clearAllThreadLocals();
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/parse-xml")
    @Operation(summary = "парсинг xml ссылки в xml разметку.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Успешно."),
            @ApiResponse(responseCode = "400", description = "Запрос выполнен с ошибками.")})
    public String parseXmlLink(@RequestParam(value = "link") String link) {
        try {
            UUID uuid = UUID.randomUUID();
            ParsingXmlLink parsingXmlLink = new ParsingXmlLink(redisTemplate, link, uuid);
            redisTemplate.opsForValue().set(uuid.toString(), "not finished", 60, TimeUnit.MINUTES);
            parsingXmlLink.thread.start();
            return uuid.toString();
        } catch (Exception e) {
            log.error("parsing xml fail with exception: {}", e.getMessage());
            return "parsing xml fail";
        }
    }

    @PostMapping("/get-xml")
    @Operation(summary = "получить xml разметку по uuid")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Успешно."),
            @ApiResponse(responseCode = "400", description = "Запрос выполнен с ошибками.")})
    public String getXml(@RequestParam(value = "uuid") String uuid) {
        try {
            if (Objects.requireNonNull(redisTemplate.opsForValue().get(uuid)).equals("not finished")) {
                return "parsing is not finished";
            }
            return redisTemplate.opsForValue().get(uuid);
        } catch (Exception e) {
            log.error("get xml fail: {}", e.getMessage());
            return "getting xml failed";
        }
    }
}