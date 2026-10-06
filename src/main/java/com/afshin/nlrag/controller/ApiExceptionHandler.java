package com.afshin.nlrag.controller;

import com.afshin.nlrag.a2a.A2aProtocolException;
import com.afshin.nlrag.ingestion.RejectedUploadException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(A2aProtocolException.class)
    ProblemDetail a2aProtocol(A2aProtocolException ex) {
        HttpStatus status = ex.code() == -32001 ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        return problem(status, ex.getMessage());
    }

    @ExceptionHandler(RejectedUploadException.class)
    ProblemDetail rejectedUpload(RejectedUploadException ex) {
        return problem(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidBody(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + " " + err.getDefaultMessage())
                .findFirst()
                .orElse("Request is invalid");
        return problem(HttpStatus.BAD_REQUEST, detail);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail uploadTooLarge() {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "Upload exceeds the configured size limit");
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(status.getReasonPhrase());
        return pd;
    }
}
