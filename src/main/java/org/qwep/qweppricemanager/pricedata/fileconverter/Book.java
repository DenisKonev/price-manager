package org.qwep.qweppricemanager.pricedata.fileconverter;


import com.opencsv.exceptions.CsvException;
import lombok.Getter;
import org.qwep.qweppricemanager.pricedata.PriceDto;
import org.qwep.qweppricemanager.pricedata.PriceProcessingException;
import org.qwep.qweppricemanager.pricesender.ClassificationItem;
import org.qwep.qweppricemanager.pricesender.configuration.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

@Getter
public abstract class Book {

    protected String name;

    protected Book(String name, PriceSupportedType type, FileProcessor fileProcessor, InputStream inputStream)
            throws IOException, CsvException {
        setName(name);
        setProcessor(fileProcessor);
        setType(type);
        setPriceBook(inputStream);
        setHeader();
    }

    protected void setName(String fileName) {
        this.name = fileName;
    }

    protected abstract void setType(PriceSupportedType priceSupportedType);

    protected abstract void setProcessor(FileProcessor fileProcessor);

    protected abstract void setPriceBook(InputStream inputStream) throws IOException, CsvException;

    protected abstract void setHeader();

    /**
     * Вернуть пустую классификацию(price header)
     */
    public abstract List<ClassificationItem> getClassification();

    public abstract List<PriceDto> getPriceDtos(List<ClassificationItem> classification,
                                                Optional<List<Configuration>> optionalConfigurations,
                                                String currency) throws PriceProcessingException;

    protected List<PriceDto> removeWrong(List<PriceDto> priceDtos) {
        priceDtos.removeIf(priceDto ->
                priceDto.getBrand() == null || priceDto.getBrand().isBlank()
                        || priceDto.getArticle() == null || priceDto.getArticle().isBlank()
                        || priceDto.getPartname() == null || priceDto.getPartname().isBlank()
                        || priceDto.getQuantity() == null
                        || priceDto.getPrice() == null);
        return priceDtos;
    }
}
