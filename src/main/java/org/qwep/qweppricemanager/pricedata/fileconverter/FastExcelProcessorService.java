package org.qwep.qweppricemanager.pricedata.fileconverter;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.dhatim.fastexcel.reader.Cell;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.qwep.qweppricemanager.pricedata.PriceDto;
import org.qwep.qweppricemanager.pricedata.PriceHeader;
import org.qwep.qweppricemanager.pricesender.ClassificationItem;
import org.qwep.qweppricemanager.pricesender.configuration.Configuration;
import org.qwep.qweppricemanager.pricesender.configuration.PriceConfService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Stream;

@Slf4j
@Service
public class FastExcelProcessorService implements FileProcessor {

    private final PriceConfService priceConfService;

    @Autowired
    public FastExcelProcessorService(PriceConfService priceConfService) {
        this.priceConfService = priceConfService;
    }

    private String classify(String input, List<ClassificationItem> classificationItems) {
        List<ClassificationItem> clitems = classificationItems.parallelStream()
                .filter(clitem -> input.trim().equals(clitem.getPriceHeader())).toList();
        return clitems.isEmpty()
                ? "trash"
                : clitems
                .stream()
                .findFirst()
                .orElse(new ClassificationItem("BLANK", "trash"))
                .getCategory();
    }

    public List<PriceDto> calculatePriceDtos(Row header,
                                             List<ClassificationItem> classificationItems,
                                             Optional<List<Configuration>> optionalConfigurations,
                                             String currency,
                                             org.dhatim.fastexcel.reader.Sheet dataSheet) throws IOException {

        List<PriceDto> priceData = new ArrayList<>();
        LinkedList<String> headerStrList = examineRow(header);
        int currentDataRowNum = 0;
        dataSheet.read().forEach(row -> {
            if (row != null) {
                PriceDto priceDto = convertRowToDto(
                        row,
                        headerStrList,
                        classificationItems,
                        currency
                );
                if (optionalConfigurations.isPresent()) {
                    for (Configuration configuration : optionalConfigurations.get()) {
                        priceDto = priceConfService.applyConf(configuration, priceDto);
                    }
                }
                priceData.add(priceDto);
            } else {
                log.debug("got null Row on rowIndex: {}", currentDataRowNum);
            }
        });
        return priceData;
    }

    private PriceDto convertRowToDto(Row row,
                                     LinkedList<String> headerStrList,
                                     List<ClassificationItem> classificationItems,
                                     String currency) {
        PriceDto dto = new PriceDto();
        dto.setCurrency(currency);
        row.forEach(cell -> {
            String columnName = cell != null && cell.getColumnIndex() <= headerStrList.size() - 1
                    ? headerStrList.get(cell.getColumnIndex())
                    : "trash";

            String cat = classify(columnName, classificationItems);
            switch (PriceHeader.valueOf(cat != null ? cat.toUpperCase() : PriceHeader.TRASH.cat.toUpperCase())) {
                case BRAND -> {
                    if (dto.getBrand() == null) {
                        dto.setBrand(extractDataFromCell(cell));
                    }
                }
                case ARTICLE -> {
                    if (dto.getArticle() == null) {
                        String val = extractDataFromCell(cell);
                        try {
                            val = val.replaceAll("(?<=\\d+)\\.0$", "");
                            dto.setArticle(val.replaceAll("[^а-яА-ЯёЁa-zA-Z0-9]", ""));
                        } catch (Exception e) {
                            dto.setArticle(val);
                        }

                    }
                }
                case PARTNAME -> {
                    if (dto.getPartname() == null) {
                        dto.setPartname(extractDataFromCell(cell).trim());
                    }
                }
                case QUANTITY -> {
                    if (dto.getQuantity() == null && cell.toString().matches("[0-9]+[,]+[0-9]+")) {
                        Long longQuantity = (long) Math.floor(Double.parseDouble(cell.toString().replaceAll(",", ".")));
                        dto.setQuantity(String.valueOf(longQuantity));
                    } else if (dto.getQuantity() == null)
                        dto.setQuantity(extractDataFromCell(cell));
                }
                case MULTIPLICITY -> {
                    if (dto.getMultiplicity() == null) {
                        dto.setMultiplicity(extractDataFromCell(cell));
                    }
                }
                case DELIVERY -> {
                    if (dto.getDelivery() == null) {
                        dto.setDelivery(extractDataFromCell(cell));
                    }
                }
                case STATUS -> {
                    if (dto.getStatus() == null) {
                        dto.setStatus(extractDataFromCell(cell));
                    }
                }
                case WAREHOUSE -> {
                    if (dto.getWarehouse() == null) {
                        dto.setWarehouse(extractDataFromCell(cell));
                    }
                }
                case PRICE -> {
                    if (dto.getPrice() == null) {
                        dto.setPrice(extractDataFromCell(cell));
                    }
                }
                case NOTES -> {
                    if (dto.getNotes() == null) {
                        dto.setNotes(extractDataFromCell(cell));
                    }
                }
                case PHOTO -> {
                    if (dto.getPhoto() == null) {
                        dto.setPhoto(extractDataFromCell(cell));
                    }
                }
                case CURRENCY -> {
                    if (dto.getCurrency() == null && currency == null) {
                        dto.setCurrency(extractDataFromCell(cell));
                    } else {
                        dto.setCurrency(currency);
                    }
                }
                case TRASH ->
                        log.trace("cell {} did not match any valuable price data category", cell == null ? " is nll" : cell.getAddress());

            }
        });
        return dto;
    }

    public String extractDataFromCell(Cell cell) {
        String val;
        switch (cell.getType()) {
            case STRING, NUMBER, FORMULA -> val = cell.getRawValue();
            case BOOLEAN -> val = String.valueOf(Boolean.parseBoolean(cell.getRawValue()));
            default -> val = "NO_TYPE_MATCHED";
        }
        return val;
    }

    public Row findPriceHeaderRow(ReadableWorkbook wkb) {
        Optional<org.dhatim.fastexcel.reader.Sheet> firstSheet = wkb.getSheet(0);
        Optional<Row> optionalRow = findPriceHeaderRow(firstSheet.orElseThrow(), 0);
        Optional<Row> validOR = searchValidRow(optionalRow, firstSheet, 5);
        if (validOR.isEmpty()) return searchValidRow(optionalRow, firstSheet, 3).orElseThrow();
        return validOR.orElseThrow();
    }

    private Optional<Row> searchValidRow(Optional<Row> optionalRow,
                                         Optional<org.dhatim.fastexcel.reader.Sheet> firstSheet,
                                         int cellsSize) {
        int deep = 0;
        while (deep != 10) {
            List<Cell> cells = optionalRow
                    .orElseThrow()
                    .stream()
                    .filter(cell -> cell != null && cell.getRawValue() != null && !cell.getRawValue().isBlank())
                    .toList();
            if (cells.size() < cellsSize) optionalRow = findPriceHeaderRow(firstSheet.orElseThrow(), deep++);
            else return optionalRow;
        }
        return Optional.empty();

    }

    private Optional<Row> findPriceHeaderRow(org.dhatim.fastexcel.reader.Sheet sheet, int deep) {
        try (Stream<Row> rows = sheet.openStream()) {
            return rows.skip(deep).findFirst();
        } catch (IOException e) {
            log.error(e.getMessage());
            return Optional.empty();
        }
    }

    private LinkedList<String> examineRow(Row row) {
        LinkedList<String> headerList = new LinkedList<>();
        LinkedList<String> finalHeaderList;
        if (row != null) {
            int attempts = 0;
            headerList = indentationCell(headerList, row, attempts);
            finalHeaderList = headerList;
            row.stream().forEach(cell -> {
                if (cell != null) {
                    finalHeaderList.add(extractDataFromCell(cell));
                }
            });
            return finalHeaderList;
        }
        return headerList;
    }

    private LinkedList<String> indentationCell(LinkedList<String> headerList, Row row, int attempts) {
        if (row.getCell(attempts) == null && attempts < 5) {
            headerList.add("BLANK");
            indentationCell(headerList, row, ++attempts);
        }
        return headerList;
    }

    public ReadableWorkbook convertToFastWorkbook(InputStream inputStream, PriceSupportedType type) throws IOException {
        ZipSecureFile.setMinInflateRatio(0.0);
        if (Objects.requireNonNull(type) == PriceSupportedType.XLSX) {
            try {
                return new ReadableWorkbook(inputStream);
            } catch (IOException exception) {
                log.error("failed to create workbook object with cause: '{}'", exception.getLocalizedMessage());
                throw exception;
            }
        }
        throw new IllegalArgumentException("Can't conver workbook");
    }
}
