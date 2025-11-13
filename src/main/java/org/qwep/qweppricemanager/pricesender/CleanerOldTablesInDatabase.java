package org.qwep.qweppricemanager.pricesender;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.qwep.qweppricemanager.pricedata.PriceDataService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@EnableScheduling
public class CleanerOldTablesInDatabase {

    private final PriceSenderInfoRepository pir;
    private final PriceDataService priceDataService;
    private final DataSource dataSource;
    @Value("${cron.enabled}")
    private boolean cronEnabled;

    public CleanerOldTablesInDatabase(PriceSenderInfoRepository pir,
                                      PriceDataService priceDataService,
                                      DataSource dataSource) {
        this.pir = pir;
        this.priceDataService = priceDataService;
        this.dataSource = dataSource;
    }

    @Scheduled(cron = "0 2 * * 6 ?")
    @Transactional
    public void deleteOldTables() {
        if(cronEnabled) {
            List<String> nameTables = getAllTables();
            List<PriceSenderInfoEntity> psieList = pir.findAll();
            if (psieList.isEmpty()) return;
//      пробегаемся по всем таблицам
            for (String table : nameTables) {
                boolean isContainTable = isContain(psieList, table);
                boolean conditions = !isContainTable
                        && !table.equals("price_file")
                        && !table.equals("price_sender_info")
                        && !table.equals("token_2_code");
//            если такой таблицы нету в price_sender_info, удаляем!
                if (conditions) {
                    UUID tableUUID = UUID.fromString(table.trim());
                    priceDataService.dropPriceTable(tableUUID);
                }
            }
        }
    }

    private boolean isContain(List<PriceSenderInfoEntity> psieList, String table) {
//            пробегаемся по таблицам в price_sender_info
        for (PriceSenderInfoEntity priceSenderInfo : psieList) {
            if (priceSenderInfo.getPriceTableRefs() == null ||
                    priceSenderInfo.getPriceTableRefs().length < 1) return false;
            String tableInPsi = priceSenderInfo.getPriceTableRefs()[0]
                    .replaceAll("[\\[\\]]", "").trim();
//                если нашли таблицу, стопаем цикл, чтобы не тратить ресурсы
            if (table.equals(tableInPsi)) {
                return true;
            }
        }
        return false;
    }

    @SneakyThrows
    @Transactional
    public List<String> getAllTables() {
        List<String> tablesName = new ArrayList<>();
        String query = "SELECT * FROM pg_catalog.pg_tables where schemaname = 'public'";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setMaxRows(1);
            ResultSet set = preparedStatement.executeQuery();
            while (set.next()) {
                tablesName.add(set.getString("tablename"));
            }
        } catch (SQLException e) {
            log.info("search tables failed: {}", e.getMessage());
        }
        return tablesName;
    }
}
