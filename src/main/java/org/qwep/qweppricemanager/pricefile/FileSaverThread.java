package org.qwep.qweppricemanager.pricefile;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;

@Slf4j
public class FileSaverThread implements Runnable {
    public Thread thread;
    private final PriceFileService priceFileService;
    private final PriceFile priceFile;

    public FileSaverThread(PriceFileService priceFileService, PriceFile priceFile) {
        this.priceFileService = priceFileService;
        this.priceFile = priceFile;
        this.thread = new Thread(this, "FileSaverThread for " + priceFile.getName());
        log.debug("Constructed: {}", thread.getName());
    }

    @Override
    public void run() {
        log.info("Started: {}", thread.getName());
        try {
            priceFileService.save(priceFile);
        } catch (IllegalArgumentException exception) {
            log.info("Tried to save wrong file: {} exception: {}",
                    priceFile.getName(), ExceptionUtils.getStackTrace(exception));
        } catch (RuntimeException exception) {
            log.error("Can't save priceFile: {} exception: {} trace: {}",
                    priceFile.getName(), exception.getMessage(), ExceptionUtils.getStackTrace(exception));
        }
        log.info("Finished: {}", thread.getName());
    }
}
