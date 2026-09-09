package uk.gov.defra.trade.imports.ins.backend.notification;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Paginated dashboard list response. {@link AggregatedNotification} is serialized directly — it
 * is already the read-model shape the dashboard needs, with no separate payload to strip.
 */
public record AggregatedNotificationPageResponse(
    List<AggregatedNotification> content,
    int page,
    int size,
    int numberOfElements,
    long totalElements,
    int totalPages) {

    public static AggregatedNotificationPageResponse from(Page<AggregatedNotification> pageResult) {
        return new AggregatedNotificationPageResponse(
            pageResult.getContent(),
            pageResult.getNumber() + 1,
            pageResult.getSize(),
            pageResult.getNumberOfElements(),
            pageResult.getTotalElements(),
            pageResult.getTotalPages());
    }
}
