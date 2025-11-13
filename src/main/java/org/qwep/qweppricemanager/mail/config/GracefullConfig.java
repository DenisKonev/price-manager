package org.qwep.qweppricemanager.mail.config;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.qwep.qweppricemanager.pricesender.PriceSenderInfoEntity;
import org.qwep.qweppricemanager.pricesender.PriceSenderService;
import org.qwep.qweppricemanager.pricesender.enums.PriceState;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PreDestroy;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Setter
@Configuration
@RequiredArgsConstructor
public class GracefullConfig {

    private ExecutorService executorService;
    private final PriceSenderService priceSenderService;
    public String adminCode = null;

    @PreDestroy
    public void onDestroy() {
        if (executorService != null) {
            Optional<PriceSenderInfoEntity> psiOpt = priceSenderService.getPriceSenderInfoEntity(adminCode);
            if (psiOpt.isPresent() && psiOpt.get().getCurrentState().equals(PriceState.IN_PROGRESS.state)) {
                priceSenderService.updateState(adminCode, PriceState.ERROR);
                log.error("The service with the admin code: {}" +
                                "did not have time to process the price in 2 minutes and therefore goes to status ERROR",
                        adminCode);
            }
            executorService.shutdown();
        } else {
            executorService = Executors.newVirtualThreadPerTaskExecutor();
        }
    }
}
