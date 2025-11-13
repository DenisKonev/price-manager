package org.qwep.qweppricemanager.pricedata.fileconverter;

import com.opencsv.exceptions.CsvException;
import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeUtility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.OldExcelFormatException;
import org.qwep.qweppricemanager.pricedata.PriceFileStream;
import org.qwep.qweppricemanager.pricedata.PriceProcessingException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookBuilder {

    private final ExcelProcessorService excelProcessorService;
    private final CsvProcessorService csvProcessorService;
    private final FastExcelProcessorService fastExcelProcessorService;

    public Book build(String filename, PriceSupportedType type, InputStream inputStream)
            throws PriceProcessingException, OldExcelFormatException {
        try {
            return switch (type) {
                case XLS -> new ExcelBook(filename,
                        type,
                        excelProcessorService,
                        inputStream);
                case XLSX -> new FastExcelBook(filename,
                        type,
                        fastExcelProcessorService,
                        inputStream);
                case CSV -> new CsvBook(filename,
                        type,
                        csvProcessorService,
                        inputStream);
            };
        } catch (IOException | CsvException exception) {
            log.error("Can't build book with fileName: {}, type: {}", filename, type);
            throw new PriceProcessingException(
                    "Can't build book with fileName: " + filename + ", type: " + type,
                    exception);
        }
    }

    public Book build(Multipart file) throws MessagingException, IOException, PriceProcessingException {
        PriceFileStream priceFileStream = getPriceFileStream(file);
        return build(priceFileStream.getFileName(), priceFileStream.getFileType(), priceFileStream.getInputStream());
    }

    public Book build(PriceFileStream priceFileStream) throws MessagingException, IOException, PriceProcessingException {
        return build(priceFileStream.getFileName(), priceFileStream.getFileType(), priceFileStream.getInputStream());
    }

    private PriceFileStream getPriceFileStream(Multipart attachment) throws IllegalArgumentException, MessagingException,
            IOException {
        try {
            if (!attachment.getContentType().contains("multipart"))
                throw new IllegalArgumentException("No multipart in message: ");

            for (int i = 0; i < attachment.getCount(); i++) {
                BodyPart part = attachment.getBodyPart(i);

                if (!Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition()))
                    continue;

                String fileNameDecoded = decode(part.getFileName()).orElseThrow();

                if (Arrays
                        .stream(PriceSupportedType.values())
                        .anyMatch(type -> fileNameDecoded.toLowerCase().contains(type.fmt))) {
                    if (fileNameDecoded.toLowerCase().contains(PriceSupportedType.XLSX.fmt)) {
                        log.info("got valid message attachment: '{}'", fileNameDecoded);
                        return new PriceFileStream(fileNameDecoded, PriceSupportedType.XLSX, part.getInputStream());
                    } else if (fileNameDecoded.toLowerCase().contains(PriceSupportedType.XLS.fmt)) {
                        log.info("got valid message attachment: '{}'", fileNameDecoded);
                        return new PriceFileStream(fileNameDecoded, PriceSupportedType.XLS, part.getInputStream());
                    } else if (fileNameDecoded.toLowerCase().contains(PriceSupportedType.CSV.fmt)) {
                        log.info("got valid message attachment: '{}'", fileNameDecoded);
                        return new PriceFileStream(fileNameDecoded, PriceSupportedType.CSV, part.getInputStream());
                    }
                    log.info("got valid message attachment: '{}'", fileNameDecoded);
                }
            }
            throw new IllegalArgumentException("No proper file in preview");
        } catch (MessagingException | IOException | IllegalArgumentException exception) {
            log.info("Can't getPriceFileStream for preview because: {}, ", exception.getMessage());
            throw exception;
        }
    }

    private Optional<String> decode(String encodedStr) {
        String decodedFileName;
        try {
            decodedFileName = MimeUtility.decodeText(encodedStr);
        } catch (UnsupportedEncodingException uex) {
            log.error("decoding message content: '{}' error: {}", encodedStr, uex.getLocalizedMessage());
            return Optional.empty();
        }
        return Optional.of(Normalizer.normalize(decodedFileName, Normalizer.Form.NFC));
    }
}
