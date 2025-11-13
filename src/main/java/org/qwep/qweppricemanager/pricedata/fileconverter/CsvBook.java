package org.qwep.qweppricemanager.pricedata.fileconverter;

import com.opencsv.exceptions.CsvException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.qwep.qweppricemanager.pricedata.PriceProcessingException;
import org.qwep.qweppricemanager.pricedata.PriceDto;
import org.qwep.qweppricemanager.pricesender.ClassificationItem;
import org.qwep.qweppricemanager.pricesender.configuration.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

@Getter
@Slf4j
public class CsvBook extends Book {
    private PriceSupportedType type;
    private List<String[]> workbook;
    private CsvProcessorService csvProcessorService;
    LinkedList<String> header;

    public CsvBook(String fileName, PriceSupportedType type, FileProcessor fileProcessor, InputStream inputStream) throws IOException, CsvException {
        super(fileName, type, fileProcessor, inputStream);
    }


    @Override
    protected void setType(PriceSupportedType priceSupportedType) {
        this.type = priceSupportedType;
    }

    @Override
    protected void setProcessor(FileProcessor fileProcessor) {
        this.csvProcessorService = (CsvProcessorService) fileProcessor;
    }

    @Override
    protected void setPriceBook(InputStream inputStream) throws IOException, CsvException {
        this.workbook = csvProcessorService.convertToCsvArray(inputStream);
    }

    @Override
    protected void setHeader() {
        header = csvProcessorService.findPriceHeaderRow(workbook);
    }

    @Override
    public List<ClassificationItem> getClassification() {
        List<ClassificationItem> classificationItemList = new ArrayList<>();
        header.forEach(header -> {
            ClassificationItem clitem = new ClassificationItem();
            clitem.setPriceHeader(
                    csvProcessorService.extractDataFromHeader(header).trim()
            );
            classificationItemList.add(clitem);
        });
        return classificationItemList;
    }

    @Override
    public List<PriceDto> getPriceDtos(List<ClassificationItem> classification,
                                       Optional<List<Configuration>> optionalConfigurations,
                                       String currency) throws PriceProcessingException {
        try {
            return removeWrong(
                    csvProcessorService.calculatePriceDtos(
                            header, workbook, optionalConfigurations, classification, currency
                    )
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
