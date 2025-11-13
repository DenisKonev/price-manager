package org.qwep.qweppricemanager.pricedata.fileconverter;

import com.opencsv.exceptions.CsvException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.OldExcelFormatException;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
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
public class FastExcelBook extends Book {
    private PriceSupportedType type;
    private FastExcelProcessorService fastExcelProcessorService;
    private Row header;
    private ReadableWorkbook fastWorkBook;

    public FastExcelBook(String filename, PriceSupportedType type, FileProcessor fileProcessor, InputStream inputStream)
            throws IOException, CsvException, OldExcelFormatException {
        super(filename, type, fileProcessor, inputStream);
    }

    @Override
    protected void setType(PriceSupportedType priceSupportedType) {
        this.type = priceSupportedType;
    }

    @Override
    protected void setProcessor(FileProcessor fileProcessor) {
        this.fastExcelProcessorService = (FastExcelProcessorService) fileProcessor;
    }

    @Override
    protected void setPriceBook(InputStream inputStream) throws IOException {
        log.debug("starting to create workbook");
        this.fastWorkBook = fastExcelProcessorService.convertToFastWorkbook(inputStream, type);
        log.debug("finished to create workbook");
    }

    @Override
    protected void setHeader() {
        header = fastExcelProcessorService.findPriceHeaderRow(fastWorkBook);
    }

    @Override
    public List<ClassificationItem> getClassification() {
        List<ClassificationItem> classificationItemList = new ArrayList<>();
        header.forEach(cell -> {
            ClassificationItem clitem = new ClassificationItem();
            if (cell != null) {
                clitem.setPriceHeader(fastExcelProcessorService.extractDataFromCell(cell).trim());
                classificationItemList.add(clitem);
            }
        });
        return classificationItemList;
    }

    @Override
    public List<PriceDto> getPriceDtos(List<ClassificationItem> classification,
                                       Optional<List<Configuration>> optionalConfigurations, String currency)
            throws PriceProcessingException {

        try {
            log.debug("Starting to get priceDto");
            return removeWrong(fastExcelProcessorService
                    .calculatePriceDtos(
                            header,
                            classification,
                            optionalConfigurations,
                            currency,
                            fastWorkBook.getFirstSheet())
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
