package uk.gov.defra.trade.imports.ins.backend.consumer;

public class SqsRetryableException extends RuntimeException {

    public SqsRetryableException(String message) {
        super(message);
    }

    public SqsRetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}
