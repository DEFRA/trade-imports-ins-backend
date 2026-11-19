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

    public AggregatedNotificationPageResponse findOrganisationPage(
        int page, String sort, String referenceNumber, String organisationId) {
        Pageable pageable = PageRequest.of(page - 1, listPageSize, AggregatedNotificationSort.toSort(sort));

        String trimmedReference = StringUtils.trimToNull(referenceNumber);
        if (trimmedReference != null) {
            log.debug(
                "Fetching notification by reference {} for dashboard organisationId={}",
                trimmedReference,
                organisationId);
            Optional<AggregatedNotification> match =
                repository.findByReferenceNumberAndOrganisationIdAndStatusNot(
                    trimmedReference, organisationId, DELETED_STATUS);
            return AggregatedNotificationPageResponse.from(
                asSingleFirstPage(match, pageable));
        }

        log.debug(
            "Fetching notifications page {} (size {}) with sort {} organisationId={}",
            page,
            listPageSize,
            sort,
            organisationId);
        Page<AggregatedNotification> result =
            repository.findAllByOrganisationIdAndStatusNot(organisationId, DELETED_STATUS, pageable);
        return AggregatedNotificationPageResponse.from(result);
    }

    private static Page<AggregatedNotification> asSingleFirstPage(
        Optional<AggregatedNotification> match, Pageable pageable) {
        Pageable firstPage = PageRequest.of(0, pageable.getPageSize(), pageable.getSort());
        return match
            .<Page<AggregatedNotification>>map(
                notification -> new PageImpl<>(List.of(notification), firstPage, 1))
            .orElseGet(() -> Page.empty(firstPage));
    }
}
