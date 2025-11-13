package org.qwep.qweppricemanager.pricefile;

import org.qwep.qweppricemanager.pricefile.PriceFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface PriceFileRepository extends JpaRepository<PriceFile, Long> {
    @Transactional
    void deleteByPriceSenderInfoEntityId(Long id);
    PriceFile findByPriceSenderInfoEntityId(Long id);
}