package com.afshin.nlrag.ingestion;

/**
 * Client sent a file this service will not parse (type, size, or empty).
 */
public class RejectedUploadException extends RuntimeException {

    public RejectedUploadException(String message) {
        super(message);
    }
}
