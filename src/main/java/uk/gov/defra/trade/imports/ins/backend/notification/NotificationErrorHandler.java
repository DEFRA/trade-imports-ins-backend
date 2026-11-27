package uk.gov.defra.trade.imports.ins.backend.notification;

import io.awspring.cloud.sqs.listener.SqsHeaders;
import io.awspring.cloud.sqs.listener.errorhandler.ErrorHandler;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationErrorHandler implements ErrorHandler<Object> {

    private final NotificationMessageMetrics messageMetrics;

    @Override
    public void handle(Message<Object> message, Throwable t) {
        Throwable classified = findClassifiedCause(t);
        Object aggregateId = message.getHeaders().get(SqsHeaders.MessageSystemAttributes.SQS_MESSAGE_GROUP_ID_HEADER);
        UUID messageId = message.getHeaders().getId();
        if (classified instanceof SqsRetryableException retryableException) {
            messageMetrics.recordRetried();
            log.warn("Retryable error, message left for retry: aggregateId={}, messageId={}",
                aggregateId, messageId, t);
            throw retryableException;
        }

        messageMetrics.recordDiscarded();
        log.error("Non-retryable error, message will be deleted: aggregateId={}, messageId={}",
            aggregateId, messageId, t);
    }

    private Throwable findClassifiedCause(Throwable t) {
        Throwable current = t;
        while (current != null) {
            if (current instanceof SqsRetryableException || current instanceof SqsNonRetryableException) {
                return current;
            }
            current = current.getCause();
        }
        return t;
    }
}
