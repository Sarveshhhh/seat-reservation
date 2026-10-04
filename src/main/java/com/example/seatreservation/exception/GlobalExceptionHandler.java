package com.example.seatreservation.exception;

import com.example.seatreservation.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> handleDomainException(DomainException ex, HttpServletRequest request) {
        String requestId = getRequestId(request);
        log.warn("Domain exception occurred [{}]: {} - Request ID: {}", ex.getCode(), ex.getMessage(), requestId);
        ErrorResponse body = new ErrorResponse(ex.getCode(), ex.getMessage(), requestId);
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String requestId = getRequestId(request);
        String errorMessage = ex.getBindingResult().getFieldErrors().isEmpty()
                ? "Invalid request parameters"
                : ex.getBindingResult().getFieldErrors().get(0).getDefaultMessage();

        log.warn("Validation error: {} - Request ID: {}", errorMessage, requestId);
        ErrorResponse body = new ErrorResponse("INVALID_REQUEST", errorMessage, requestId);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        String requestId = getRequestId(request);
        log.warn("Malformed JSON payload - Request ID: {}", requestId);
        ErrorResponse body = new ErrorResponse("INVALID_REQUEST", "Malformed JSON request body", requestId);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request) {
        String requestId = getRequestId(request);
        log.warn("Data integrity violation: {} - Request ID: {}", ex.getMessage(), requestId);
        ErrorResponse body = new ErrorResponse("CONFLICT", "Request conflicted with existing database constraints", requestId);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        String requestId = getRequestId(request);
        log.error("Unexpected server error - Request ID: {}", requestId, ex);
        ErrorResponse body = new ErrorResponse("INTERNAL_SERVER_ERROR", "An unexpected error occurred. Please try again later.", requestId);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private String getRequestId(HttpServletRequest request) {
        String reqId = (String) request.getAttribute("X-Request-Id");
        if (reqId == null || reqId.isBlank()) {
            reqId = request.getHeader("X-Request-Id");
        }
        return (reqId != null && !reqId.isBlank()) ? reqId : UUID.randomUUID().toString();
    }
}
