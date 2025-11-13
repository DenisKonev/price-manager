package org.qwep.qweppricemanager.pricedata.fileconverter;

import com.opencsv.exceptions.CsvException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.OldExcelFormatException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.qwep.qweppricemanager.pricedata.PriceDto;
import org.qwep.qweppricemanager.pricedata.PriceProcessingException;
import org.qwep.qweppricemanager.pricesender.ClassificationItem;
import org.qwep.qweppricemanager.pricesender.configuration.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
public class ExcelBook extends Book {
    private PriceSupportedType type;
    private Workbook workbook;
    private ExcelProcessorService excelProcessorService;
    private Row header;

    public ExcelBook(String filename, PriceSupportedType type, FileProcessor fileProcessor, InputStream inputStream)
            throws IOException, CsvException, OldExcelFormatException {
        super(filename, type, fileProcessor, inputStream);
    }

    @Override
    protected void setType(PriceSupportedType priceSupportedType) {
        this.type = priceSupportedType;
    }

    @Override
    protected void setProcessor(FileProcessor fileProcessor) {
        this.excelProcessorService = (ExcelProcessorService) fileProcessor;
    }

    @Override
    protected void setPriceBook(InputStream inputStream) throws IOException {
        log.debug("starting to create workbook");
        this.workbook = excelProcessorService.convertToWorkbook(inputStream, type);
        log.debug("finished to create workbook");
    }

    @Override
    protected void setHeader() {
        header = excelProcessorService.findPriceHeaderRow(workbook);
    }

    @Override
    public List<ClassificationItem> getClassification() {
        List<ClassificationItem> classificationItemList = new ArrayList<>();
        header.cellIterator().forEachRemaining(cell -> {
            ClassificationItem clitem = new ClassificationItem();
            clitem.setPriceHeader(
                    excelProcessorService.extractDataFromCell(cell).trim()
            );
            classificationItemList.add(clitem);
        });
        return classificationItemList;
    }

    @Override
    public List<PriceDto> getPriceDtos(List<ClassificationItem> classification,
                                       Optional<List<Configuration>> optionalConfigurations, String currency)
            throws PriceProcessingException {
        try {
            log.debug("Starting to get priceDto");
            return removeWrong(
                    excelProcessorService.calculatePriceDtos(header, classification, optionalConfigurations, currency)
            );
        } catch (Exception exception) {
            PriceProcessingException priceProcessingException = new PriceProcessingException(
                    "Can't get priceDTO with classification: " + classification
                            + " conf: " + optionalConfigurations
                            + " bookname: " + getName(),
                    exception
            );
            log.error(priceProcessingException.getMessage() + "exception is {}", exception.getMessage());
            throw priceProcessingException;
        }
    }
}
