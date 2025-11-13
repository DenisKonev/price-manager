package org.qwep.qweppricemanager.search.exception;


import lombok.RequiredArgsConstructor;
import org.qwep.qweppricemanager.pricedata.PriceDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class SearchRepository {

    private final JdbcTemplate jdbcTemplate;


    public List<PriceDto> findItemsByArticle(String table, String article, Boolean isCrosses) {
        String query;
        if (isCrosses) {
            query = String.format("SELECT * FROM \"%s\" WHERE article = ?", table);
        } else {
            article = "%" + article + "%";
            query = String.format("SELECT * FROM \"%s\" WHERE article LIKE ?", table);
        }

        String finalArticle = article;
        return jdbcTemplate.query(query, ps -> {
            ps.setString(1, finalArticle);
        }, (resultSet, rowNum) -> mapPriceItem(tableName2StringArray(table), resultSet));
    }

    public List<PriceDto> findItemsByBrandAndArticle(String table, String article, String brand, Boolean isCrosses) {

        String query;
        if (isCrosses) {
            query = String.format("SELECT * FROM \"%s\" WHERE brand = ? and article = ?", table);
        } else {
            article = "%" + article + "%";
            query = String.format("SELECT * FROM \"%s\" WHERE brand = ? and article LIKE ?", table);
        }

        String finalArticle = article;
        return jdbcTemplate.query(query, ps -> {
            ps.setString(1, brand);
            ps.setString(2, finalArticle);
                }, (resultSet, rowNum) ->
                mapPriceItem(tableName2StringArray(table), resultSet)
        );
    }

    private PriceDto mapPriceItem(String[] tables, ResultSet resultSet) throws SQLException {
        PriceDto priceDto = new PriceDto();
        priceDto.setId(resultSet.getLong("id"));
        priceDto.setBrand(resultSet.getString("brand"));
        priceDto.setArticle(resultSet.getString("article"));
        priceDto.setPartname(resultSet.getString("partname"));
        priceDto.setQuantity(resultSet.getString("quantity"));
        priceDto.setMultiplicity(resultSet.getString("multiplicity"));
        priceDto.setDelivery(resultSet.getString("delivery"));
        priceDto.setStatus(resultSet.getString("status"));
        priceDto.setWarehouse(resultSet.getString("warehouse"));
        priceDto.setPrice(resultSet.getString("price"));
        if (resultSet.getString("notes") != null && !resultSet.getString("notes").isEmpty()) {
            priceDto.setNotes(resultSet.getString("notes"));
        }
        priceDto.setCurrency(resultSet.getString("currency"));
        priceDto.setTableRef(tables);
        return priceDto;
    }

    private String[] tableName2StringArray(String tableNameString) {
        return new String[]{tableNameString};
    }

}
