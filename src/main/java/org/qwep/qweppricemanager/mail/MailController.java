package org.qwep.qweppricemanager.mail;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.constraints.Email;
import lombok.extern.slf4j.Slf4j;
import org.qwep.qweppricemanager.external.ApiQwepService;
import org.qwep.qweppricemanager.pricesender.PriceSenderInfoEntity;
import org.qwep.qweppricemanager.pricesender.PriceSenderService;
import org.qwep.qweppricemanager.pricesender.enums.PriceState;
import org.qwep.qweppricemanager.rest.dto.Response;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;
import java.util.UUID;

@RestController
@Slf4j
@RequestMapping("/mail")
public class MailController {
    private final PriceSenderService priceSenderService;
    private final ApiQwepService apiQwepService;
    private final MailService mailService;

    public MailController(PriceSenderService priceSenderService,
                          ApiQwepService apiQwepService,
                          MailService mailService) {
        this.priceSenderService = priceSenderService;
        this.apiQwepService = apiQwepService;
        this.mailService = mailService;
    }

    @PostMapping(value = "/process", consumes = {"multipart/form-data"})
    @Operation(summary = "Обработка почтового прайса.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Прайс принят в обработку.",
                    content = {@Content(mediaType = "text",
                            schema = @Schema(implementation = Response.class))}),
            @ApiResponse(responseCode = "400", description = "Запрос выполнен с ошибками.",
                    content = @Content)})
    public ResponseEntity<Response<Object>> process(
            @RequestParam(value = "admin-code", required = false) String adminCode,
            @RequestParam(value = "price-list-file") MultipartFile multipart,
            @RequestParam(value = "vendor-email") @Email String email) {
        try {
            log.info("Starting process mail price with adminCode: {} and email: {}", adminCode, email);
            Optional<PriceSenderInfoEntity> psiOpt = priceSenderService.getPriceSenderInfoEntity(adminCode);
            if (psiOpt.isEmpty()) psiOpt = priceSenderService.getPriceSenderByEmail(email);

            if (psiOpt.isEmpty()) {
                Optional<String> vendorIdByEmail = apiQwepService.getVendorIdByEmail(String.format("%%%s%%", email));
                if (vendorIdByEmail.isEmpty()) {
                    log.info("No client for adminCode: {} email: {} from mail", adminCode, email);
                    return ResponseEntity
                            .badRequest()
                            .body(new Response<>(false, "No client with this creds"));
                }
                log.error("Found client with email {} and adminCode: {} from parser-service",
                        email, adminCode);

                psiOpt = priceSenderService.getPriceSenderInfoEntity(UUID.fromString(vendorIdByEmail.get()));
                if (psiOpt.isEmpty()) {
                    log.info("No client for adminCode: {} email: {} and vendorID: {} from mail",
                            adminCode, email, vendorIdByEmail);
                    return ResponseEntity
                            .badRequest()
                            .body(new Response<>(false, "No client with this creds"));
                }

                priceSenderService.setEmailIdentification(psiOpt.get().getAdminCode(), true);
            }

            PriceSenderInfoEntity psi = psiOpt.get();
            return switch (PriceState.valueOf(psi.getCurrentState().toUpperCase())) {
                case REGISTERED -> mailService.processRegistered(psi, multipart);
                case AWAITS_CLASSIFICATION -> ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(new Response<>(false, "Classify price first"));
                case IN_PROGRESS -> ResponseEntity
                        .status(HttpStatus.TOO_EARLY)
                        .body(new Response<>(false, "Price is in progress, try later"));
                case ERROR, ERROR_EMPTY, ERROR_NO_REF, ERROR_SEARCH_EMPTY, ERROR_NO_TABLE, ERROR_EMPTY_STATE -> {
                    if (psi.getClassificationListJsonString().isEmpty())
                        yield mailService.processRegistered(psi, multipart);
                    else yield mailService.processUpload(psi, multipart);
                }
                case CHANGED, UNCHANGED -> mailService.processUpload(psi, multipart);
                case ERROR_95 -> ResponseEntity
                        .badRequest()
                        .body(new Response<>(
                                false,
                                multipart.getOriginalFilename() + "not supported, use newer version"));
            };
        } catch (Exception exception) {
            log.error("Can't process mail price with adminCode: {}, email: {} because exception: {}",
                    adminCode, email, exception.getMessage());
            return ResponseEntity
                    .internalServerError()
                    .body(new Response<>(false, exception.getMessage()));
        }
    }
}
