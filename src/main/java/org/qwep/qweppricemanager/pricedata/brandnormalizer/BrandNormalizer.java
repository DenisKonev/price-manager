package org.qwep.qweppricemanager.pricedata.brandnormalizer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.qwep.qweppricemanager.data.dto.RedisNormalizedBrandDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

@Service
@Slf4j
public class BrandNormalizer {
    private final RedisTemplate<String, String> redisMasterTemplate;
    private final Map<String, String> normalizationMap;
    private final HashSet<String> normalizationFailedBrandSet;
    private final ObjectMapper mapper;

    public BrandNormalizer(@Qualifier("redis.master") RedisTemplate<String, String> redisMasterTemplate) {
        this.redisMasterTemplate = redisMasterTemplate;
        this.normalizationMap = new HashMap<>();
        this.normalizationFailedBrandSet = new HashSet<>();
        this.mapper = new ObjectMapper();
    }

    public String normalize(String brand) {
        try {
            if (normalizationFailedBrandSet.contains(brand))
                return brand;

            String normalizedBrand = normalizationMap.get(brand);
            if (normalizedBrand == null) {
                RedisNormalizedBrandDto redisBrandDto = mapper.readValue(redisMasterTemplate.opsForValue()
                                .get("qwep:company:" + Base64.getEncoder().encodeToString(brand.trim().toLowerCase().getBytes())),
                        RedisNormalizedBrandDto.class);
                normalizationMap.put(brand, redisBrandDto.getName());
            }
            normalizedBrand = normalizationMap.get(brand);
            if (normalizedBrand == null) {
                normalizationFailedBrandSet.add(brand);
                return brand;
            }
            log.debug("normalizing brand '{}' to '{}'", brand, normalizationMap.get(brand));
            return normalizedBrand;
        } catch (Exception e) {
            log.debug("failed to normalize brand with cause: {}", e.getLocalizedMessage());
            return brand;
        }
    }

    public String getCacheSize() {
        return "normalizationMap size: " + normalizationMap.size() + "\n"
                + "normalizationFailedBrandSet size: " + normalizationFailedBrandSet.size();
    }
}
