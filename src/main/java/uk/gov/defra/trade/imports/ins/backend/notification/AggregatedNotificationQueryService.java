package uk.gov.defra.trade.imports.ins.backend.notification;

import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/** Serves {@code GET /notifications} for the unified dashboard. */
@Slf4j
@Service
public class AggregatedNotificationQueryService {

    private static final String DELETED_STATUS = "DELETED";

    private final AggregatedNotificationRepository repository;
    private final int listPageSize;

    public AggregatedNotificationQueryService(
        AggregatedNotificationRepository repository,
        @Value("${notification.list.page-size}") int listPageSize) {
        this.repository = repository;
        this.listPageSize = listPageSize;
    }

    public AggregatedNotificationPageResponse findAll(int page, String sort, String referenceNumber) {
        Pageable pageable = PageRequest.of(page - 1, listPageSize, AggregatedNotificationSort.toSort(sort));

        String trimmedReference = StringUtils.trimToNull(referenceNumber);
        if (trimmedReference != null) {
            log.debug("Fetching notification by reference {} for dashboard", trimmedReference);
            Optional<AggregatedNotification> match =
                repository.findByReferenceNumberAndStatusNot(trimmedReference, DELETED_STATUS);
            Page<AggregatedNotification> matched = match
                .<Page<AggregatedNotification>>map(notification -> new PageImpl<>(List.of(notification), pageable, 1))
                .orElseGet(() -> Page.empty(pageable));
            return AggregatedNotificationPageResponse.from(matched);
        }

        log.debug("Fetching notifications page {} (size {}) with sort {}", page, listPageSize, sort);
        Page<AggregatedNotification> result = repository.findAllByStatusNot(DELETED_STATUS, pageable);
        return AggregatedNotificationPageResponse.from(result);
    }
}
