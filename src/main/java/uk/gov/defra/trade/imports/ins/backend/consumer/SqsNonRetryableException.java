package uk.gov.defra.trade.imports.ins.backend.consumer;

public class SqsNonRetryableException extends RuntimeException {

    public SqsNonRetryableException(String message) {
        super(message);
    }

    public SqsNonRetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}
