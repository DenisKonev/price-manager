package org.qwep.qweppricemanager.pricesender;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface PriceSenderInfoRepository extends JpaRepository<PriceSenderInfoEntity, Long> {


    Optional<PriceSenderInfoEntity> findPriceSenderInfoByVendorId(String vendorId);

    Optional<PriceSenderInfoEntity> findPriceSenderInfoEntityByAdminCode(String adminCode);

    List<PriceSenderInfoEntity> findPriceSenderInfoEntitiesByAdminCodeIn(List<String> adminCodeList);

    List<PriceSenderInfoEntity> findAllByVendorIdIn(List<String> vendorIds);

    Optional<PriceSenderInfoEntity> findPriceSenderInfoEntityByEmailAndEmailIdentificationIsTrue(String email);

    @Transactional
    long deleteByAdminCode(String adminCode);

    boolean existsByVendorIdIs(String vendorId);

    boolean existsByEmail(String email);

    boolean existsByEmailAndEmailIdentificationIsTrue(String email);

    List<PriceSenderInfoEntity> findAllByEmail(String email);


    Optional<PriceSenderInfoEntity> findPriceSenderInfoEntityByVendorId(String uuid);

    default boolean isPriceProcessed(Optional<String> vendorId) {
        if (vendorId.isPresent()) {
            Optional<PriceSenderInfoEntity> psi = findPriceSenderInfoEntityByVendorId(vendorId.get());
            return psi.filter(priceSenderInfoEntity -> priceSenderInfoEntity.getPriceTableRefs().length > 0).isPresent();
        } else {
            return false;
        }
    }
}
