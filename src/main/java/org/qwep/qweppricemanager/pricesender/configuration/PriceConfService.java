package org.qwep.qweppricemanager.pricesender.configuration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.qwep.qweppricemanager.conversion.CurrencyConversionService;
import org.qwep.qweppricemanager.pricedata.PriceDto;
import org.qwep.qweppricemanager.pricedata.PriceHeader;
import org.qwep.qweppricemanager.pricesender.PriceSenderInfoEntity;
import org.springframework.stereotype.Service;

import javax.money.Monetary;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class PriceConfService {
    private final ObjectMapper objectMapper;
    private final CurrencyConversionService currencyConversionService;

    public PriceConfService(CurrencyConversionService currencyConversionService) {
        objectMapper = new ObjectMapper();
        this.currencyConversionService = currencyConversionService;
    }

    public Optional<List<Configuration>> buildConf(String confJson) {
        if (confJson == null) return Optional.empty();
        try {
            return Optional.of(List.of(objectMapper.readValue(confJson, Configuration[].class)));
        } catch (JsonProcessingException exception) {
            log.error("Can't build price conf from {} with error: {}", confJson, exception.getMessage());
            return Optional.empty();
        }
    }

    public PriceDto applyConf(Configuration conf, PriceDto priceDto) {
        try {
            return switch (conf.getType()) {
                case AddValue -> setPriceDTOField(
                        priceDto,
                        PriceHeader.valueOf(conf.getCategory().toUpperCase()),
                        conf.getValue()
                );
                case AddValueIfEmpty -> {
                    if (getField(priceDto, PriceHeader.valueOf(conf.getCategory().toUpperCase())) == null ||
                            getField(priceDto, PriceHeader.valueOf(conf.getCategory().toUpperCase())).equals("BLANK"))
                        yield setPriceDTOField(priceDto,
                                PriceHeader.valueOf(conf.getCategory().toUpperCase()),
                                conf.getValue()
                        );
                    else yield priceDto;
                }
                case CurrencyConversion -> {
                    String price = currencyConversionService
                            .convert(
                                    priceDto.getPrice(),
                                    Monetary.getCurrency(conf.getValue()),
                                    Monetary.getCurrency(priceDto.getCurrency()));
                    priceDto.setPrice(price);
                    priceDto.setCurrency(conf.getValue());
                    yield priceDto;
                }
                default -> priceDto;
            };
        } catch (Exception exception) {
            log.error(
                    "Can't apply configuration with conf: {} and priceDTO: {} and exception: {}",
                    conf,
                    priceDto,
                    exception.getMessage()
            );
            return priceDto;
        }
    }

    private PriceDto setPriceDTOField(PriceDto priceDto, PriceHeader priceHeader, String value) {
        return switch (priceHeader) {
            case BRAND -> {
                priceDto.setBrand(value);
                yield priceDto;
            }
            case ARTICLE -> {
                priceDto.setArticle(value);
                yield priceDto;
            }
            case PARTNAME -> {
                priceDto.setPartname(value);
                yield priceDto;
            }
            case QUANTITY -> {
                priceDto.setQuantity(value);
                yield priceDto;
            }
            case MULTIPLICITY -> {
                priceDto.setMultiplicity(value);
                yield priceDto;
            }
            case DELIVERY -> {
                priceDto.setDelivery(value);
                yield priceDto;
            }
            case STATUS -> {
                priceDto.setStatus(value);
                yield priceDto;
            }
            case WAREHOUSE -> {
                priceDto.setWarehouse(value);
                yield priceDto;
            }
            case PRICE -> {
                priceDto.setPrice(value);
                yield priceDto;
            }
            case NOTES -> {
                priceDto.setNotes(value);
                yield priceDto;
            }
            case PHOTO -> {
                priceDto.setPhoto(value);
                yield priceDto;
            }
            case CURRENCY -> {
                priceDto.setCurrency(value);
                yield priceDto;
            }
            default -> priceDto;

        };
    }

    private String getField(PriceDto priceDto, PriceHeader priceHeader) {
        return switch (priceHeader) {
            case BRAND -> priceDto.getBrand();
            case ARTICLE -> priceDto.getArticle();
            case PARTNAME -> priceDto.getPartname();
            case QUANTITY -> priceDto.getQuantity();
            case MULTIPLICITY -> priceDto.getMultiplicity();
            case DELIVERY -> priceDto.getDelivery();
            case STATUS -> priceDto.getStatus();
            case WAREHOUSE -> priceDto.getWarehouse();
            case PRICE -> priceDto.getPrice();
            case NOTES -> priceDto.getNotes();
            case PHOTO -> priceDto.getPhoto();
            case CURRENCY -> priceDto.getCurrency();
            default -> null;
        };
    }

    public Optional<Configuration> getCleanPeriodConf(List<Configuration> configurations) {
        List<Configuration> cleanPeriodConf = configurations
                .stream()
                .filter(configuration -> configuration.getType() == ConfigurationType.CleanPeriod)
                .toList();
        return switch (cleanPeriodConf.size()) {
            case 0 -> Optional.empty();
            case 1 -> Optional.of(cleanPeriodConf.get(0));
            default -> {
                log.error("Found more then 1 cleanPeriodConf in {}", configurations);
                yield Optional.empty();
            }
        };
    }

    public Optional<Configuration> getCleanPeriodConf(PriceSenderInfoEntity psi) {
        Optional<List<Configuration>> optionalConfigurations =
                buildConf(psi.getConfigurationsJsonString());
        if (!optionalConfigurations.isPresent())
            return Optional.empty();

        return getCleanPeriodConf(optionalConfigurations.get());

    }

    /**
     * Conf list shouldn't affect two categories twice and have more than one periodCleanConf
     * Also checks category spelling
     */
    public void checkConfigurations(List<Configuration> configurations) throws IllegalArgumentException {
        for (Configuration configuration : configurations) {
            switch (configuration.getType()) {
                case AddValue, AddValueIfEmpty -> {
                    PriceHeader.valueOf(configuration.getCategory().toUpperCase());
                    checkIfCategoryIsUnique(configurations, configuration);
                }
                case CleanPeriod -> {
                    List<Configuration> periodCleanConf =
                            configurations
                                    .stream()
                                    .filter(configuration3 ->
                                            configuration3.getType() == ConfigurationType.CleanPeriod)
                                    .toList();
                    if (periodCleanConf.size() > 1)
                        throw new IllegalArgumentException(
                                "There are two cleanPeriod confs, but should be one: " + periodCleanConf
                        );
                }
                case CurrencyConversion -> {
                    List<Configuration> periodCurrencyConversion =
                            configurations
                                    .stream()
                                    .filter(configuration4 ->
                                            configuration4.getType().equals(ConfigurationType.CurrencyConversion))
                                    .toList();
                    if (periodCurrencyConversion.size() > 1)
                        throw new IllegalArgumentException(
                                "There are two CurrencyConversion confs, but should be one: "
                                        + periodCurrencyConversion);
                }
            }
        }
    }

    private void checkIfCategoryIsUnique(List<Configuration> configurations, Configuration configuration)
            throws IllegalArgumentException {
        boolean met = false;
        for (Configuration configuration1 : configurations) {
            if (configuration.getCategory().equals(configuration1.getCategory())) {
                if (!met) met = true;
                else throw new IllegalArgumentException(
                        "These configurations affect same category: "
                                + configuration + " and " + configuration1);
            }
        }
    }

}
