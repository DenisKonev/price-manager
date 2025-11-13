package org.qwep.qweppricemanager.pricefile;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PriceFileService {
    private final PriceFileRepository priceFileRepository;

    @Autowired
    public PriceFileService(PriceFileRepository priceFileRepository) {
        this.priceFileRepository = priceFileRepository;
    }

    public void save(PriceFile priceFile) throws IllegalArgumentException {
        Validate.notBlank(priceFile.getName());
        Validate.notNull(priceFile.getFile());
        Validate.notNull(priceFile.getPriceSenderInfoEntity());
        priceFileRepository.save(priceFile);
    }

    public PriceFile getByPsiId(long psiId) throws RuntimeException {
        return priceFileRepository.findByPriceSenderInfoEntityId(psiId);
    }

    public void removeByPsiId(long psiId) throws RuntimeException {
        priceFileRepository.deleteByPriceSenderInfoEntityId(psiId);
    }

}
