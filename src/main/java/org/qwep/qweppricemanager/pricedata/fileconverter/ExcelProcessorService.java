package org.qwep.qweppricemanager.pricedata.fileconverter;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class ExcelProcessorService implements FileProcessor {

    private final PriceConfService priceConfService;

    @Autowired
    public ExcelProcessorService(PriceConfService priceConfService) {
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
                                             String currency) {

        List<PriceDto> priceData = new ArrayList<>();
        LinkedList<String> headerStrList = examineRow(header);
        Sheet dataSheet = header.getSheet();
        int currentDataRowNum = header.getRowNum();
        int lastDataRowNum = dataSheet.getLastRowNum();

        while (currentDataRowNum++ <= lastDataRowNum) {
            Row row = dataSheet.getRow(currentDataRowNum);
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
        }

        return priceData;
    }

    private PriceDto convertRowToDto(Row row,
                                     LinkedList<String> headerStrList,
                                     List<ClassificationItem> classificationItems,
                                     String currency) {
        PriceDto dto = new PriceDto();
        dto.setCurrency(currency);
        row.cellIterator().forEachRemaining(cell -> {
            String columnName = cell.getColumnIndex() <= headerStrList.size() - 1
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
                        log.trace("cell {} did not match any valuable price data category", cell.getAddress().formatAsR1C1String());

            }
        });
        return dto;
    }

    public String extractDataFromCell(Cell cell) {
        String val;
        switch (cell.getCellType()) {
            case STRING -> val = cell.getStringCellValue();

            case NUMERIC -> {
                cell.setCellType(CellType.STRING);
                val = String.valueOf(cell.getStringCellValue());
            }
            case BOOLEAN -> val = Boolean.toString(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    val = ((XSSFCell) cell).getRawValue();
                } catch (ClassCastException classCastException) {
                    log.error(classCastException.getMessage() + "\n" + cell);
                    val = "BLANK";
                }
            }
            case BLANK -> val = "BLANK";
            case _NONE -> val = "_NONE";
            case ERROR -> val = String.valueOf(cell.getErrorCellValue());
            default -> val = "NO_TYPE_MATCHED";
        }
        return val;
    }

    public Row findPriceHeaderRow(Workbook wkb) {
        Sheet firstSheet = wkb.getSheetAt(0);
        Optional<Row> optionalRow = findPriceHeaderRow(firstSheet, 5);

        if (optionalRow.isEmpty())
            optionalRow = findPriceHeaderRow(firstSheet, 3);


        return optionalRow.orElseGet(() -> firstSheet.getRow(firstSheet.getFirstRowNum()));

    }

    private Optional<Row> findPriceHeaderRow(Sheet sheet, int priceHeaderSize) {
        int rowIndex = sheet.getFirstRowNum();
        while (rowIndex <= sheet.getPhysicalNumberOfRows()) {
            LinkedList<String> rowVals = examineRow(sheet.getRow(rowIndex));
            if (!rowVals.isEmpty()
                    && rowVals
                    .stream()
                    .filter(val -> !val.equals("BLANK") && !val.equals("0") && !val.isEmpty())
                    .toList()
                    .size() >= priceHeaderSize
//                    && rowVals.size() >= 4
            ) {
                log.info("found header row on rowIndex: {}", rowIndex);

                Row head = sheet.getRow(rowIndex);
                AtomicInteger i = new AtomicInteger();
                head.cellIterator().forEachRemaining(cell -> {
                    if (extractDataFromCell(cell).equals("BLANK"))
                        cell.setCellValue("BLANK" + i.incrementAndGet());
                });
                return Optional.of(head);
            } else {
                rowIndex++;
            }
        }
        return Optional.empty();
    }

    //@LogExecTimeAspect
    private LinkedList<String> examineRow(Row row) {
        LinkedList<String> headerList = new LinkedList<>();
        if (row != null) {
            int attempts = 0;
            headerList = indentationCell(headerList, row, attempts);
            Iterator<Cell> cellIterator = row.cellIterator();
            while (cellIterator.hasNext()) {
                Cell cell = cellIterator.next();
                if (cell != null) {
                    headerList.add(extractDataFromCell(cell));
                } else {
                    log.warn("expected header row has non-string values -> skip");
                    return new LinkedList<>();
                }
            }
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


    public Workbook convertToWorkbook(InputStream inputStream, PriceSupportedType type) throws IOException {
        ZipSecureFile.setMinInflateRatio(0.0);
        switch (type) {
            case XLS -> {
                try {
                    return new HSSFWorkbook(inputStream);
                } catch (IOException exception) {
                    log.error("failed to create workbook object with cause: '{}'", exception.getLocalizedMessage());
                    throw exception;
                }
            }
            case XLSX -> {
                try {
                    return new XSSFWorkbook(inputStream);
                } catch (IOException exception) {
                    log.error("failed to create workbook object with cause: '{}'", exception.getLocalizedMessage());
                    throw exception;
                }
            }
            default -> throw new IllegalArgumentException("Can't conver workbook");

        }
    }
}
