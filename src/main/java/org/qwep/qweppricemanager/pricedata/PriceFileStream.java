package org.qwep.qweppricemanager.pricedata;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.qwep.qweppricemanager.pricedata.fileconverter.PriceSupportedType;
import org.qwep.qweppricemanager.pricefile.PriceFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Getter
@AllArgsConstructor
public class PriceFileStream {
    private String fileName;
    private PriceSupportedType fileType;
    private InputStream inputStream;

    public PriceFileStream(String fileName, InputStream inputStream) throws IllegalArgumentException, IOException {
        if (fileName.contains(".zip")) {
            ZipInputStream zis = new ZipInputStream(inputStream);
            ZipEntry zipEntry = zis.getNextEntry();
            this.fileName = zipEntry.getName();
            this.fileType = getFileTypeFromName(zipEntry.getName());
            this.inputStream = zis;
            return;
        }

        this.inputStream = inputStream;
        this.fileName = fileName;
        this.fileType = getFileTypeFromName(fileName);
    }

    public PriceFileStream(PriceFile priceFile) throws IllegalArgumentException, IOException {
        if(priceFile.getName().contains(".zip")){
            ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(priceFile.getFile()));
            ZipEntry zipEntry = zis.getNextEntry();
            this.fileName = zipEntry.getName();
            this.fileType = getFileTypeFromName(zipEntry.getName());
            this.inputStream = zis;
            return;
        }

        this.fileName = priceFile.getName();
        this.fileType = getFileTypeFromName(priceFile.getName());
        this.inputStream = new ByteArrayInputStream(priceFile.getFile());
    }

    private PriceSupportedType getFileTypeFromName(String name) throws IllegalArgumentException {
        if (name.toLowerCase().contains(PriceSupportedType.XLSX.fmt)) {
            return PriceSupportedType.XLSX;
        } else if (name.toLowerCase().contains(PriceSupportedType.XLS.fmt)) {
            return PriceSupportedType.XLS;
        } else if (name.toLowerCase().contains(PriceSupportedType.CSV.fmt)) {
            return PriceSupportedType.CSV;
        } else {
            throw new IllegalArgumentException("Price does not support such files");
        }
    }

}
