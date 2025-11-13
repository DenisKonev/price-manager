package org.qwep.qweppricemanager.data;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.qwep.qweppricemanager.pricedata.PriceQueriesConfig;
import org.qwep.qweppricemanager.pricesender.PriceSenderInfoEntity;
import org.qwep.qweppricemanager.pricesender.enums.PriceStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class Core {

    private final PriceQueriesConfig priceQueriesConfig;
    private final RedisTemplate<String, String> redisInternalTemplate;
    private final DataSource dataSource;


    @Autowired
    public Core(PriceQueriesConfig priceQueriesConfig,
                @Qualifier("redis.internal") RedisTemplate<String, String> redisInternalTemplate,
                DataSource dataSource) {
        this.priceQueriesConfig = priceQueriesConfig;
        this.redisInternalTemplate = redisInternalTemplate;
        this.dataSource = dataSource;
    }

    //@LogExecTimeAspect
    @SneakyThrows
    @Transactional
    public Integer countRowsQuery(String query) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setMaxRows(1);
            ResultSet rs = stmt.executeQuery();
            rs.next();
            Integer result = rs.getInt(1);
            rs.close();
            return result;
        } catch (SQLException sqlex) {
            log.warn("failed to count data rows trace: '{}'", sqlex.getLocalizedMessage());
            return 0;
        }
    }

    @SneakyThrows
    @Transactional
    public void dropPriceTable(String tableName) {
        String query = priceQueriesConfig.getDropPriceTableQuery().replace("param_price_param", tableName);
        try (Connection connection = dataSource.getConnection(); PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setMaxRows(16);
            stmt.execute();
            log.info("table {} dropped", tableName);
        } catch (SQLException sqlex) {
            log.error("error while dropping price table '{}' trace: {}", tableName, sqlex.getLocalizedMessage());
        }
    }

    @Deprecated
    public PriceStatus getPriceStatus(PriceSenderInfoEntity psi) {
        if (psi.getClassificationListJsonString().contains("null")) {
            return PriceStatus.UNCLASSIFIED;
        } else if (psi.getPriceTableRefs().length == 0 || countPriceTableDataRows(List.of(psi.getPriceTableRefs())) == 0) {
            return PriceStatus.EMPTY;
        } else if (psi.getLastUpdated().isBefore(LocalDateTime.now().minusDays(2))) {
            return PriceStatus.OLD;
        } else {
            return PriceStatus.PUBLISHED;
        }
    }

    public Integer countPriceTableDataRows(List<String> priceTableRefs) {
        return priceTableRefs.parallelStream()
                .map(tableRef -> countRowsQuery("select count(*) from \"" + tableRef + "\"")).toList().stream()
                .reduce(0, Integer::sum);
    }


    public String getPriceUpdateProgress(String adminCode) {
        String progressVal = redisInternalTemplate.opsForValue().get(adminCode);
        return progressVal != null ? progressVal : "0.0";
    }


}