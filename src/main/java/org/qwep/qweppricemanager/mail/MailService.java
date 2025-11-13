package org.qwep.qweppricemanager.mail;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.qwep.qweppricemanager.pricefile.FileSaverThread;
import org.qwep.qweppricemanager.GetAndSavePriceHeadersClassificationAndFile;
import org.qwep.qweppricemanager.UploadThread;
import org.qwep.qweppricemanager.pricedata.PriceDataService;
import org.qwep.qweppricemanager.pricedata.PriceFileStream;
import org.qwep.qweppricemanager.pricedata.fileconverter.BookBuilder;
import org.qwep.qweppricemanager.pricefile.PriceFile;
import org.qwep.qweppricemanager.pricefile.PriceFileService;
import org.qwep.qweppricemanager.pricesender.PriceSenderInfoEntity;
import org.qwep.qweppricemanager.pricesender.PriceSenderService;
import org.qwep.qweppricemanager.pricesender.configuration.PriceConfService;
import org.qwep.qweppricemanager.rest.dto.Response;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Slf4j
@Service
public class MailService {
    private final PriceFileService priceFileService;
    private final BookBuilder bookBuilder;
    private final PriceSenderService priceSenderService;
    private final PriceConfService priceConfService;
    private final PriceDataService priceDataService;
    private final MailSender mailSender;

    public MailService(PriceFileService priceFileService,
                       BookBuilder bookBuilder,
                       PriceSenderService priceSenderService,
                       PriceConfService priceConfService,
                       PriceDataService priceDataService,
                       MailSender mailSender) {
        this.priceFileService = priceFileService;
        this.bookBuilder = bookBuilder;
        this.priceSenderService = priceSenderService;
        this.priceConfService = priceConfService;
        this.priceDataService = priceDataService;
        this.mailSender = mailSender;
    }

    public ResponseEntity<Response<Object>> processRegistered(PriceSenderInfoEntity psi, MultipartFile multipart) {
        try {
            FileSaverThread fileSaverThread = new FileSaverThread(
                    priceFileService,
                    new PriceFile(multipart.getOriginalFilename(), psi, multipart.getBytes())
            );
            PriceFileStream priceFileStream = new PriceFileStream(
                    multipart.getOriginalFilename(),
                    multipart.getInputStream());
            GetAndSavePriceHeadersClassificationAndFile gsphcf = new GetAndSavePriceHeadersClassificationAndFile(
                    bookBuilder,
                    psi.getAdminCode(),
                    priceFileStream,
                    fileSaverThread,
                    priceSenderService
            );
            gsphcf.thread.start();
            return ResponseEntity
                    .ok(new Response<>(true, "Started classification"));
        } catch (IOException exception) {
            log.error("Can't get inputStream for adminCode: {} exception: {} trace: {}",
                    psi.getAdminCode(), exception.getMessage(), ExceptionUtils.getStackTrace(exception));
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new Response<>(false, exception.getMessage()));
        }
    }

    public ResponseEntity<Response<Object>> processUpload(PriceSenderInfoEntity psi, MultipartFile multipart) {
        try {
            PriceFileStream priceFileStream =
                    new PriceFileStream(multipart.getOriginalFilename(), multipart.getInputStream());
            UploadThread uploadThread = new UploadThread(
                    priceConfService,
                    priceFileStream,
                    psi,
                    bookBuilder,
                    priceDataService,
                    priceSenderService,
                    mailSender
            );
            uploadThread.thread.start();
            return ResponseEntity
                    .ok(new Response<>(true, "Price starts updating"));
        } catch (IOException exception) {
            log.error("Can't get inputStream for adminCode: {} exception: {} trace: {}",
                    psi.getAdminCode(), exception.getMessage(), ExceptionUtils.getStackTrace(exception));
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new Response<>(false, exception.getMessage()));
        }
    }

}
