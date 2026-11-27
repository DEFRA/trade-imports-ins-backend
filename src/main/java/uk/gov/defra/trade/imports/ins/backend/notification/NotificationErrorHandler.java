package uk.gov.defra.trade.imports.ins.backend.notification;

import io.awspring.cloud.sqs.listener.SqsHeaders;
import io.awspring.cloud.sqs.listener.errorhandler.ErrorHandler;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
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
        Optional<SqsRetryableException> retryable = classifiedCause(t)
            .filter(SqsRetryableException.class::isInstance)
            .map(SqsRetryableException.class::cast);
        Object aggregateId = message.getHeaders().get(SqsHeaders.MessageSystemAttributes.SQS_MESSAGE_GROUP_ID_HEADER);
        UUID messageId = message.getHeaders().getId();
        if (retryable.isPresent()) {
            messageMetrics.recordRetried();
            log.warn("Retryable error, message left for retry: aggregateId={}, messageId={}",
                aggregateId, messageId, t);
            throw retryable.get();
        }

        messageMetrics.recordDiscarded();
        log.error("Non-retryable error, message will be deleted: aggregateId={}, messageId={}",
            aggregateId, messageId, t);
    }

    private Optional<Throwable> classifiedCause(Throwable t) {
        return Stream.iterate(t, Objects::nonNull, Throwable::getCause)
            .filter(cause -> cause instanceof SqsRetryableException || cause instanceof SqsNonRetryableException)
            .findFirst();
    }
}
