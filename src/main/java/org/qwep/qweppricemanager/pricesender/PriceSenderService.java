package org.qwep.qweppricemanager.pricesender;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.qwep.qweppricemanager.data.Core;
import org.qwep.qweppricemanager.external.UserApiService;
import org.qwep.qweppricemanager.mail.service.CoreProcessorService;
import org.qwep.qweppricemanager.pricedata.PriceDataService;
import org.qwep.qweppricemanager.pricefile.PriceFileRepository;
import org.qwep.qweppricemanager.pricesender.configuration.Configuration;
import org.qwep.qweppricemanager.pricesender.configuration.PriceConfService;
import org.qwep.qweppricemanager.pricesender.enums.PriceState;
import org.qwep.qweppricemanager.pricesender.enums.PriceStatus;
import org.qwep.qweppricemanager.rest.dto.ChangeQuantityItemDto;
import org.qwep.qweppricemanager.search.exception.AlreadyErrorStateException;
import org.qwep.qweppricemanager.search.exception.AwaitClassificationStateException;
import org.qwep.qweppricemanager.search.exception.EmptyCurrentStateException;
import org.qwep.qweppricemanager.search.exception.InProgressStateException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@AllArgsConstructor
public class PriceSenderService {
    private final PriceFileRepository fileRepository;
    private final PriceSenderInfoRepository repository;
    private final CoreProcessorService coreProcessorService;
    private final Core core;
    private final PriceDataService priceDataService;
    private final PriceConfService priceConfService;
    private final UserApiService userApiService;
    private final KafkaTemplate<String, String> kafkaTemplate;


    private final ObjectMapper mapper = new ObjectMapper();

    public Optional<PriceSenderInfoEntity> getPriceSenderInfoEntity(String adminCode) {
        return repository.findPriceSenderInfoEntityByAdminCode(adminCode);
    }

    public Optional<PriceSenderInfoEntity> getPriceSenderInfoEntity(UUID vendorId) {
        return repository.findPriceSenderInfoByVendorId(vendorId.toString());
    }

    public void savePriceSenderInfoEntity(Optional<PriceSenderInfoEntity> psiOpt) {
        psiOpt.ifPresent(repository::save);
    }

    public void savePriceSenderInfoEntity(PriceSenderInfoEntity psi) {
        repository.save(psi);
    }

    public PriceSenderInfoEntity add(String name,
                                     String email,
                                     String adminCode,
                                     String vendorId,
                                     String viewCode,
                                     String currency,
                                     Optional<String> accountId) throws JsonProcessingException {
        if (isEmailEngaged(email)) throw new IllegalArgumentException("This email is engaged");
        PriceSenderInfoEntity psi = new PriceSenderInfoEntity();
        psi.setName(name);
        psi.setEmail(email);
        psi.setAdminCode(adminCode);
        psi.setClassificationListJsonString("[ ]");
        psi.setVendorId(vendorId);
        psi.setPriceTableRefs(new String[]{});
        psi.setViewCode(viewCode);
        psi.setPriceCurrency(currency);
        accountId.ifPresent(psi::setUserApiAccountId);
        psi.setCurrentState(PriceState.REGISTERED.state);
        psi.setEmailIdentification(false);
        repository.save(psi);
        kafkaTemplate.send("priceRegister", mapper.writeValueAsString(psi));

        return psi;
    }

    @Transactional
    public void setClassification(String adminCode, String classificationJson) throws NoSuchElementException {
        Optional<PriceSenderInfoEntity> psiOptional =
                repository.findPriceSenderInfoEntityByAdminCode(adminCode);
        if (psiOptional.isEmpty()) throw new NoSuchElementException("No psi with this adminCode");
        PriceSenderInfoEntity psi = psiOptional.get();
        psi.setClassificationListJsonString(classificationJson);
        repository.save(psi);
    }

    @Transactional
    public void updateState(String adminCode, PriceState priceState) throws NoSuchElementException {
        Optional<PriceSenderInfoEntity> psiOptional =
                repository.findPriceSenderInfoEntityByAdminCode(adminCode);
        if (psiOptional.isEmpty()) throw new NoSuchElementException("No psi with this adminCode");
        PriceSenderInfoEntity psi = psiOptional.get();
        psi.setCurrentState(priceState.state);
        if (priceState.state.equals(PriceState.ERROR_EMPTY.state)) psi.setPriceTableRefs(new String[]{});
        repository.save(psi);
    }

    @Transactional
    public void updateClassification(String adminCode, List<ClassificationItem> classificationItems)
            throws NoSuchElementException, JsonProcessingException {
        Optional<PriceSenderInfoEntity> psiOptional =
                repository.findPriceSenderInfoEntityByAdminCode(adminCode);
        if (psiOptional.isEmpty()) throw new NoSuchElementException();
        PriceSenderInfoEntity psi = psiOptional.get();
        List<ClassificationItem> priceHeaders = List.of(
                mapper.readValue(psi.getClassificationListJsonString(), ClassificationItem[].class)
        );
        if (classificationItems.size() != priceHeaders.size())
            throw new IllegalArgumentException("New classification have wrong size");

        psi.setClassificationListJsonString(mapper.writeValueAsString(classificationItems));
        repository.saveAndFlush(psi);
    }

    /**
     * sets priceTableRef, updated time to now, state to changed
     */
    @Transactional
    public void updateUploaded(String adminCode, UUID priceTableRef) throws NoSuchElementException {
        Optional<PriceSenderInfoEntity> optionalPsi =
                repository.findPriceSenderInfoEntityByAdminCode(adminCode);
        if (optionalPsi.isEmpty()) throw new NoSuchElementException("No priceSender with this adminCode");
        PriceSenderInfoEntity psi = optionalPsi.get();
        String[] priceTableRefs = new String[]{priceTableRef.toString()};
        psi.setPriceTableRefs(priceTableRefs);
        psi.setLastUpdated(LocalDateTime.now());
        psi.setCurrentState(PriceState.CHANGED.state);
        repository.save(psi);
    }

    @SneakyThrows
    public UUID getPriceTableRef(String adminCode) {
        Optional<PriceSenderInfoEntity> optionalPsi = repository.findPriceSenderInfoEntityByAdminCode(adminCode);
        if (optionalPsi.isEmpty()) throw new IllegalArgumentException("No priceSender with this adminCode");
        PriceSenderInfoEntity psi = optionalPsi.get();
        String[] priceTableRefs = psi.getPriceTableRefs();
        if (priceTableRefs.length > 1)
            log.error("There are {} priceTables for adminCode: {}", priceTableRefs.length, adminCode);
        return switch (priceTableRefs.length) {
            case 0 -> throw new NoPriceTableRefsException("No priceTables");
            case 1 -> UUID.fromString(priceTableRefs[0]);
            default -> {
                log.error("There are {} priceTables for adminCode: {}", priceTableRefs.length, adminCode);
                throw new IllegalStateException("Several priceTables for adminCode");
            }
        };
    }

    public void changeQuantity(List<ChangeQuantityItemDto> changeCountItems) {
        changeCountItems.forEach(changeQuantityItemDto ->
                priceDataService.changeItemQuantity(changeQuantityItemDto,
                        getPriceTableRefByVendorId(changeQuantityItemDto.getVendorId()))
        );

    }

    public void removePriceSender(String adminCode) throws IllegalArgumentException {
        repository.deleteByAdminCode(adminCode);
        log.info("removed price with adminCode: '{}'", adminCode);

    }

    public void validatePriceData() {
        List<PriceSenderInfoEntity> psiList = repository.findAll();
        psiList.parallelStream()
                .filter(this::isShouldBeWarned)
                .forEach(psi ->
                        coreProcessorService.sendPriceLifecycleOverWarningToClient(psi.getEmail(), psi.getAdminCode())
                );

        psiList.parallelStream()
                .filter(this::isShouldBeDropped)
                .forEach(psi -> {
                    List.of(psi.getPriceTableRefs()).forEach(core::dropPriceTable);
                    psi.setPriceTableRefs(new String[]{});
                    repository.save(psi);
                    coreProcessorService.sendPriceDropNotificationToClient(psi.getEmail(), psi.getAdminCode());
                });
    }

    private boolean isShouldBeWarned(PriceSenderInfoEntity psi) {
        if (psi.getLastUpdated() == null)
            return false;

        LocalDateTime timeToWarnFrom = LocalDateTime.now().minusDays(3);
        LocalDateTime timeToWarnTo = timeToWarnFrom.plusDays(1);

        Optional<Configuration> cleanPeriodConf = priceConfService.getCleanPeriodConf(psi);
        if (cleanPeriodConf.isEmpty())
            return psi.getLastUpdated().isAfter(timeToWarnFrom) && psi.getLastUpdated().isBefore(timeToWarnTo);

        timeToWarnFrom = LocalDateTime
                .now()
                .minusDays(Integer.parseInt(cleanPeriodConf.get().getValue()))
                .plusDays(1);
        timeToWarnTo = timeToWarnFrom.plusDays(1);

        return psi.getLastUpdated().isAfter(timeToWarnFrom) && psi.getLastUpdated().isBefore(timeToWarnTo);
    }

    private boolean isShouldBeDropped(PriceSenderInfoEntity psi) {
        if (psi.getLastUpdated() == null)
            return false;

        LocalDateTime timeToDrop = LocalDateTime.now().minusDays(3).minusHours(1);

        Optional<Configuration> cleanPeriodConf = priceConfService.getCleanPeriodConf(psi);
        if (cleanPeriodConf.isEmpty())
            return timeToDrop.isAfter(psi.getLastUpdated()) && psi.getPriceTableRefs().length > 0;

        timeToDrop = LocalDateTime.now().minusDays(Integer.parseInt(
                cleanPeriodConf.get().getValue()
        )).minusHours(1);

        return timeToDrop.isAfter(psi.getLastUpdated()) && psi.getPriceTableRefs().length > 0;
    }

    public void removePriceSender(String bearerToken, List<String> adminCodeList) {
        List<PriceSenderInfoEntity> psis =
                repository.findPriceSenderInfoEntitiesByAdminCodeIn(adminCodeList);
        if (!psis.isEmpty()) {
            psis.forEach(psi -> {
                fileRepository.deleteByPriceSenderInfoEntityId(psi.getId());
                userApiService.disableVendorUserapiAccount(psi, bearerToken.replaceAll("Bearer || ", ""));
            });
            repository.deleteAll(psis);
            log.info("handle request DELETE /remove by admin-codes: '{}'", adminCodeList);
        } else {
            log.warn("suspicious request DELETE /remove by admin-codes: '{}'", adminCodeList);
        }
    }

    public String getState(String adminCode) throws NoSuchElementException {
        return repository
                .findPriceSenderInfoEntityByAdminCode(adminCode)
                .orElseThrow()
                .getCurrentState();

    }

    @Transactional
    public void changeEmail(String adminCode, String email) {
        Optional<PriceSenderInfoEntity> optionalPsi =
                repository.findPriceSenderInfoEntityByAdminCode(adminCode);
        if (optionalPsi.isEmpty())
            throw new NoSuchElementException("Can't find psi with this adminCode");
        PriceSenderInfoEntity psi = optionalPsi.get();
        psi.setEmail(email);
        repository.save(psi);
    }

    public PriceStatus getPriceStatus(PriceSenderInfoEntity psi) {
        if (psi.getClassificationListJsonString().contains("null")) {
            return PriceStatus.UNCLASSIFIED;
        } else if (psi.getPriceTableRefs().length == 0
                || !priceDataService.isThereAnyItems(List.of(psi.getPriceTableRefs()))) {
            return PriceStatus.EMPTY;
        } else if (psi
                .getLastUpdated()
                .isBefore(LocalDateTime
                        .now()
                        .minusDays(
                                priceConfService
                                        .getCleanPeriodConf(psi)
                                        .isPresent() ?
                                        Long.parseLong(
                                                priceConfService
                                                        .getCleanPeriodConf(psi)
                                                        .orElseThrow()
                                                        .getValue()
                                        )
                                        : 2L
                        ))) {
            return PriceStatus.OLD;
        } else {
            return PriceStatus.PUBLISHED;
        }
    }

    @Transactional
    public void setEmailIdentification(String adminCode, boolean isEmailIdentification) {
        Optional<PriceSenderInfoEntity> psiOpt =
                repository.findPriceSenderInfoEntityByAdminCode(adminCode);
        if (psiOpt.isEmpty()) throw new NoSuchElementException("No price for this adminCode");
        PriceSenderInfoEntity psi = psiOpt.get();
        if (repository.findAllByEmail(psi.getEmail()).size() > 1)
            throw new IllegalArgumentException("Email is busy");
        psi.setEmailIdentification(isEmailIdentification);
        repository.save(psi);
    }

    /**
     * if email is used for identification purposes
     */
    public boolean isEmailEngaged(String email) {
        return repository.existsByEmailAndEmailIdentificationIsTrue(email);
    }

    public Optional<PriceSenderInfoEntity> getPriceSenderByEmail(String email) {
        return repository.findPriceSenderInfoEntityByEmailAndEmailIdentificationIsTrue(email);
    }

    public List<PriceSenderInfoEntity> getAllByVendorIdIn(List<String> vendorIds) {
        return repository.findAllByVendorIdIn(vendorIds);
    }

    @Transactional
    @Retryable(maxAttemptsExpression = "3", backoff = @Backoff(delayExpression = "100"))
    /**
     * обновляет ссылку на таблицу в прайсСенедере и удаляет старую таблицу
     */
    public void rotatePriceDataTable(String adminCode, PriceTableRef priceTableRef) {
        try {
            UUID priceTableRefOld = getPriceTableRef(adminCode);
            updateUploaded(adminCode, priceTableRef.getPriceTableRef());
            priceDataService.dropPriceTable(priceTableRefOld);
        } catch (NoPriceTableRefsException exception) {
            updateUploaded(adminCode, priceTableRef.getPriceTableRef());
        } catch (NoSuchElementException exception) {
            //can't find psi
            priceDataService.dropPriceTable(priceTableRef.getPriceTableRef());
        }
    }

    @SneakyThrows
    public UUID getPriceTableRefByVendorId(String vendorId) {
        Optional<PriceSenderInfoEntity> optionalPsi = repository.findPriceSenderInfoEntityByVendorId(vendorId);
        if (optionalPsi.isEmpty()) throw new IllegalArgumentException("No priceSender with this adminCode");
        PriceSenderInfoEntity psi = optionalPsi.get();
        String[] priceTableRefs = psi.getPriceTableRefs();
        if (priceTableRefs.length > 1)
            log.error("There are {} priceTables for adminCode: {}", priceTableRefs.length, vendorId);
        return switch (priceTableRefs.length) {
            case 0 -> throw new NoPriceTableRefsException("No priceTables");
            case 1 -> UUID.fromString(priceTableRefs[0]);
            default -> {
                log.error("There are {} priceTables for adminCode: {}", priceTableRefs.length, vendorId);
                throw new IllegalStateException("Several priceTables for adminCode");
            }
        };
    }

    public void checkPriceInfoState(PriceSenderInfoEntity psie) {
        String currentStateStr = psie.getCurrentState();
        if (currentStateStr == null || currentStateStr.isEmpty()) {
            throw new EmptyCurrentStateException("PriceSenderInfo state is null or empty");
        }

        if(currentStateStr.equalsIgnoreCase("awaits_classification")) {
            throw new AwaitClassificationStateException("Awaits classification state");
        }

        if (currentStateStr.toLowerCase().contains("error")) {
            throw new AlreadyErrorStateException("Current state already contains error, state is " + currentStateStr);
        }
    }

    public UUID getUUIDPriceTableRef(PriceSenderInfoEntity psie) {
        try {
            return UUID.fromString(psie.getPriceTableRefs()[0]);
        } catch (Exception exception) {
            if(psie.getCurrentState() != null && psie.getCurrentState().equals("in_progress"))
                throw new InProgressStateException("The new table is almost loaded, but the old version has been deleted, please try again");
            throw new NoPriceTableRefsException("Can't getUUIDPriceTableRef, ref uuid: exception: "
                    + exception.getMessage(), exception, psie);
        }
    }
}
