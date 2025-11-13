package org.qwep.qweppricemanager.pricedata.fileconverter;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.qwep.qweppricemanager.pricedata.PriceDto;
import org.qwep.qweppricemanager.pricedata.PriceHeader;
import org.qwep.qweppricemanager.pricesender.ClassificationItem;
import org.qwep.qweppricemanager.pricesender.configuration.Configuration;
import org.qwep.qweppricemanager.pricesender.configuration.PriceConfService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;

@Service
@Slf4j
public class CsvProcessorService implements FileProcessor {

    private final CharsetDetector charsetDetector;
    private final PriceConfService priceConfService;

    @Autowired
    public CsvProcessorService(CharsetDetector charsetDetector,
                               PriceConfService priceConfService) {
        this.charsetDetector = charsetDetector;
        this.priceConfService = priceConfService;
    }

    public List<String[]> convertToCsvArray(InputStream inputStream) throws IOException, CsvException {
        File file = new File("csv_price_" + UUID.randomUUID() + ".csv");
        try {
            FileUtils.copyInputStreamToFile(inputStream, file);
        } catch (IOException e) {
            log.error("failed to copy input stream to file because: {}", e.getLocalizedMessage());
        } finally {
            inputStream.close();
        }
        try (FileReader filereader = new FileReader(file, charsetDetector.detectCharset(file))) {
            log.info("open FileReader for file: '{}' encoded with: '{}'", file, filereader.getEncoding());
            try {
                CSVParser parser = new CSVParserBuilder()
                        .withSeparator(';')
                        .withIgnoreQuotations(true)
                        .build();

                CSVReader csvReader = new CSVReaderBuilder(filereader)
                        .withSkipLines(0)
                        .withCSVParser(parser)
                        .build();
                List<String[]> base = csvReader.readAll();
                csvReader.close();

                try {
                    log.info("remove temp file '{}' status is '{}'", file.getName(), Files.deleteIfExists(file.toPath()));
                } catch (IOException e) {
                    log.error("failed to remove price-list '{}' with cause: {}", file.getName(), e.getLocalizedMessage());
                }
                return base;
            } catch (IOException | CsvException exception) {
                log.error("failed to process csv file with cause: {}", exception.getLocalizedMessage());
                throw exception;
            }
        } catch (IOException | CsvException exception) {
            log.error("failed to process csv file with cause: {}", exception.getLocalizedMessage());
            throw exception;
        }
    }

    public LinkedList<String> findPriceHeaderRow(List<String[]> data) {
        return new LinkedList<>(Arrays.stream(data.stream().filter(row -> row.length >= 4).toList().get(0)).toList());
    }

    public String extractDataFromHeader(String header) {
        return header;
    }

    public List<PriceDto> calculatePriceDtos(LinkedList<String> headerStrList,
                                             List<String[]> workBook,
                                             Optional<List<Configuration>> confList,
                                             List<ClassificationItem> classificationData,
                                             String currency) {
        List<PriceDto> priceData = new ArrayList<>();
        int currentDataRowNum = 0;
        int lastDataRowNum = workBook.size() - 1;

        while (currentDataRowNum++ < lastDataRowNum) {
            List<String> row = List.of(workBook.get(currentDataRowNum));
            if (!row.isEmpty()) {
                PriceDto priceDto = convertRowToDto(row, headerStrList, classificationData, currency);
                if (confList.isPresent()) {
                    for (Configuration configuration : confList.get()) {
                        priceDto = priceConfService.applyConf(configuration, priceDto);
                    }
                }
                priceData.add(priceDto);
            } else {
                log.debug("got null Row on rowIndex: {}", currentDataRowNum);
            }
        }
        return priceData;
    }


    private LinkedList<String> trimHeader(LinkedList<String> headers) {
        LinkedList<String> trimmedHeaders = new LinkedList<>();
        for (int i = 0; i < headers.size(); i++) {
            trimmedHeaders.add(headers.get(i).trim());
        }
        return trimmedHeaders;
    }

    private PriceDto convertRowToDto(List<String> row, LinkedList<String> headerStrList, List<ClassificationItem> classificationItems, String currency) {
        PriceDto dto = new PriceDto();
        dto.setCurrency(currency);
        for (int i = 0; i < row.size(); i++) {
            String cell = row.get(i);
            String columnName = i <= headerStrList.size() - 1 ? headerStrList.get(i) : "trash";
            switch (PriceHeader.valueOf(classify(columnName, classificationItems).toUpperCase())) {
                case BRAND -> {
                    if (dto.getBrand() == null) {
                        dto.setBrand(cell);
                    }
                }
                case ARTICLE -> {
                    if (dto.getArticle() == null) {
                        dto.setArticle(cell);
                    }
                }
                case PARTNAME -> {
                    if (dto.getPartname() == null) {
                        dto.setPartname(cell);
                    }
                }
                case QUANTITY -> {
                    if (dto.getQuantity() == null && cell.matches("[0-9]+[,]+[0-9]+")) {
                        Long longQuantity = (long) Math.floor(Double.parseDouble(cell.replaceAll(",", ".")));
                        dto.setQuantity(String.valueOf(longQuantity));
                    } else if (dto.getQuantity() == null)
                        dto.setQuantity(cell.replaceAll("[^0-9]", ""));
                }
                case MULTIPLICITY -> {
                    if (dto.getMultiplicity() == null) {
                        dto.setMultiplicity(cell);
                    }
                }
                case DELIVERY -> {
                    if (dto.getDelivery() == null) {
                        dto.setDelivery(cell);
                    }
                }
                case STATUS -> {
                    if (dto.getStatus() == null) {
                        dto.setStatus(cell);
                    }
                }
                case WAREHOUSE -> {
                    if (dto.getWarehouse() == null) {
                        dto.setWarehouse(cell);
                    }
                }
                case PRICE -> {
                    if (dto.getPrice() == null) {
                        dto.setPrice(cell);
                    }
                }
                case NOTES -> {
                    if (dto.getNotes() == null) {
                        dto.setNotes(cell);
                    }
                }
                case PHOTO -> {
                    if (dto.getPhoto() == null) {
                        dto.setPhoto(cell);
                    }
                }
                case CURRENCY -> {
                    if (dto.getCurrency() == null && currency == null) {
                        dto.setCurrency(cell);
                    } else {
                        dto.setCurrency(currency);
                    }
                }
                case TRASH -> log.trace("csv cell value {} did not match any valuable price data category", cell);

            }
        }

        return dto;
    }

    private String classify(String input, List<ClassificationItem> classificationItems) {
        for (ClassificationItem classificationItem : classificationItems) {
            if (input.trim().equals(classificationItem.getPriceHeader().trim()))
                return classificationItem.getCategory();
        }
        return "trash";
    }
}
