package org.qwep.qweppricemanager.external;

import lombok.extern.slf4j.Slf4j;
import org.qwep.qweppricemanager.pricedata.PriceDto;
import org.qwep.qweppricemanager.pricedata.PriceQueriesConfig;
import org.qwep.qweppricemanager.pricedata.Summary;
import org.qwep.qweppricemanager.pricedata.brandnormalizer.BrandNormalizer;
import org.qwep.qweppricemanager.pricesender.PriceTableRef;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Slf4j
public class PricedbRepository {
    private final PriceQueriesConfig priceQueriesConfig;
    private final BrandNormalizer brandNormalizer;
    private final JdbcTemplate jdbcTemplate;
    private static final String TABLE_NAME = "placeholder_name";

    public PricedbRepository(PriceQueriesConfig priceQueriesConfig,
                             BrandNormalizer brandNormalizer, JdbcTemplate jdbcTemplate) {
        this.priceQueriesConfig = priceQueriesConfig;
        this.brandNormalizer = brandNormalizer;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void updateRow(String ref, long count, String brand, String article) {
        log.debug("updating rows in :{}", ref);
        jdbcTemplate.update(priceQueriesConfig.getChangeItemQuantity().replace(TABLE_NAME,
                ref), count, brand, article);
    }

    public void deleteRow(String ref, String brand, String article) {
        log.debug("deleting rows in :{}", ref);
        String deleteRow = priceQueriesConfig.getDeleteRow().replace(TABLE_NAME, ref);
        jdbcTemplate.update(deleteRow, brand, article);
    }

    public int getItemQuantity(String ref, String brand, String article) {
        log.debug("getting rows in :{}", ref);
        String getItemQuantity = priceQueriesConfig.getGetItemQuantity().replace(TABLE_NAME, ref);
        Integer quantity = jdbcTemplate.queryForObject(getItemQuantity, Integer.class, brand, article);
        return (quantity != null) ? quantity : 0;
    }

    @Transactional
    public void createEmptyPriceTable(String tableName) throws RuntimeException {
        String query = priceQueriesConfig.getCreateEmptyPriceTableQuery().replace("param_price_param", tableName);
        jdbcTemplate.execute(query);
    }

    @Transactional
    public Integer countRowsQuery(String ref) {
        String query = priceQueriesConfig.getCountRowsQuery().replace(TABLE_NAME, ref);
        return jdbcTemplate.queryForObject(query, Integer.class);
    }

    public boolean existsAndHaveRows(UUID ref) {
        if (isTableRefExist(PriceTableRef.of(ref)))
            return isThereAnyRows(ref);
        return false;
    }

    private boolean isThereAnyRows(UUID ref) {
        String query = String.format("""
                SELECT id FROM "%s" LIMIT 1;
                """, ref.toString());
        try {
            return jdbcTemplate.queryForObject(query, Integer.class) > 0;
        } catch (EmptyResultDataAccessException exception) {
            return false;
        }
    }

    @Transactional
    public void deletePriceTable(UUID priceTabeRef) {
        String tableName = priceTabeRef.toString();
        String query = priceQueriesConfig.getDropPriceTableQuery().replace("param_price_param", tableName);
        jdbcTemplate.execute(query);
        log.info("table {} dropped", tableName);
    }

    public List<PriceDto> getPriceDtos(String[] priceTableRefs) {

        String query = priceQueriesConfig.getGetPriceDtos().replace(TABLE_NAME, priceTableRefs[0]);
        return jdbcTemplate.query(query, (rs, rowNum) ->
                PriceDto.builder()
                        .tableRef(priceTableRefs)
                        .id(rs.getLong("id"))
                        .brand(rs.getString("brand"))
                        .article(rs.getString("article"))
                        .partname(rs.getString("partname"))
                        .quantity(rs.getString("quantity"))
                        .multiplicity(rs.getString("multiplicity"))
                        .delivery(rs.getString("delivery"))
                        .status(rs.getString("status"))
                        .warehouse(rs.getString("warehouse"))
                        .price(rs.getString("price"))
                        .notes(rs.getString("notes"))
                        .photo(rs.getString("photo"))
                        .currency(rs.getString("currency"))
                        .build()
        );

    }

    public Summary saveResult(List<PriceDto> priceDtos, UUID priceTableRef, Summary summary)
            throws IllegalArgumentException {
        Integer initialSize = priceDtos.size();
        List<PriceDto> priceDtosValidated = priceDtos.stream()
                .filter(PriceDto::isValid)
                .toList();
        Integer declineSize = initialSize - priceDtosValidated.size();
        summary.setLoadedDataRowsCount(declineSize);
        if (priceDtosValidated.isEmpty()) {
            throw new IllegalArgumentException("No valid price data to save");
        }

        String priceTableRefString = priceTableRef.toString();
        String insertPriceRowQuery = priceQueriesConfig.getInsertPriceQuery()
                .replace("param_price_param", priceTableRefString);

        List<Object[]> batchArgs = new ArrayList<>();

        for (PriceDto priceDto : priceDtosValidated) {
            try {
                Object[] args = buildArgsForBatch(priceDto);
                summary.incrementLoadedDataRowsCount();
                batchArgs.add(args);
            } catch (Exception ex) {
                summary.incrementDeclinedDataRowsCount();
                log.debug("Error while processing priceDto {}: {}", priceDto, ex.getMessage());
            }
        }

        try {
            jdbcTemplate.batchUpdate(insertPriceRowQuery, batchArgs);
            jdbcTemplate.execute("ANALYZE \"" + priceTableRefString + "\"");
            return summary;
        } catch (DataAccessException ex) {
            throw new RuntimeException("Can't save price data", ex);
        }
    }

    private Object[] buildArgsForBatch(PriceDto priceDto) throws ParseException {
        try {
            String normalizedBrand = brandNormalizer.normalize(priceDto.getBrand());
            return new Object[]{
                    normalizedBrand != null ? normalizedBrand : priceDto.getBrand(),
                    new String(priceDto.getArticle()
                            .replaceAll("[^0-9a-zA-Zа-яА-Я]", "")
                            .toUpperCase()
                            .getBytes(), StandardCharsets.UTF_8),
                    priceDto.getPartname(),
                    priceDto.getQuantity() != null
                            ? getLongFromString(priceDto.getQuantity().replaceAll("[^0-9]", "")) : 1,
                    priceDto.getMultiplicity() != null
                            ? getLongFromString(priceDto.getMultiplicity())
                            : 1,
                    priceDto.getDelivery() != null ? priceDto.getDelivery() : "0",
                    priceDto.getStatus() != null ? priceDto.getStatus() : "В наличии",
                    priceDto.getWarehouse() != null ? priceDto.getWarehouse() : "Склад выдачи",
                    priceDto.parseAndRoundPrice(),
                    priceDto.getNotes(),
                    priceDto.getPhoto(),
                    priceDto.getCurrency()
            };

        } catch (IllegalArgumentException | ParseException ex) {
            log.error("Error processing price DTO {}: {}", priceDto, ex.getMessage());
            throw ex;
        }
    }


    private long getLongFromString(String numberStr) throws ParseException {
        try {
            return (long) Double.parseDouble(numberStr.replace("\"", ""));
        } catch (NumberFormatException exception) {
            NumberFormat format = NumberFormat.getInstance(Locale.FRANCE);
            Number number = format.parse(numberStr);
            return number.longValue();
        }
    }

    public boolean isTableRefExist(PriceTableRef ref) {
        String query = "SELECT EXISTS (" +
                "    SELECT 1" +
                "    FROM information_schema.tables" +
                "    WHERE table_schema = 'public'" +
                "    AND table_name = ?)";
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(query, Boolean.class, ref.getPriceTableRef().toString()));
    }

}
