package org.qwep.qweppricemanager.pricesender;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.api.ErrorMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.NoSuchElementException;

@ControllerAdvice(assignableTypes = {PriceSenderController.class})
@Slf4j
public class RestExceptionHandler {
    private static final String ERROR_LOG_TEXT = "URI: {}, exception: {}";

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorMessage> handleNoSuchElementException(NoSuchElementException exception,
                                                                     HttpServletRequest request) {
        log.info(ERROR_LOG_TEXT, request.getRequestURI(), exception.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorMessage(exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorMessage> handleIllegalArgumentException(IllegalArgumentException exception,
                                                                       HttpServletRequest request) {
        log.info(ERROR_LOG_TEXT, request.getRequestURI(), exception.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorMessage(exception.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorMessage> handleException(Exception exception,
                                                        HttpServletRequest request) {
        log.info(ERROR_LOG_TEXT, request.getRequestURI(), exception.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorMessage(exception.getMessage()));
    }

    @ExceptionHandler(JsonProcessingException.class)
    public ResponseEntity<ErrorMessage> handleJsonProcessingException(JsonProcessingException exception,
                                                                      HttpServletRequest request) {
        log.info(ERROR_LOG_TEXT, request.getRequestURI(), exception.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).body(new ErrorMessage(exception.getMessage()));
    }


}

