package uk.gov.defra.trade.imports.ins.backend.notification;

import org.springframework.data.domain.Sort;

/**
 * Parses the {@code sort} query parameter sent by the dashboard
 * (e.g. {@code arrivalDate,desc}, {@code lastUpdated,asc}).
 */
public final class AggregatedNotificationSort {

    private static final String ARRIVAL_DATE_FIELD = "arrivalDate";
    private static final String LAST_UPDATED_FIELD = "lastUpdated";

    private AggregatedNotificationSort() {
    }

    public static Sort toSort(String sortParam) {
        if (sortParam == null || sortParam.isBlank()) {
            return defaultSort();
        }

        String[] parts = sortParam.split(",");
        if (parts.length != 2) {
            return defaultSort();
        }

        String sortField = parts[0].trim();
        Sort.Direction sortDirection = "asc".equalsIgnoreCase(parts[1].trim())
            ? Sort.Direction.ASC
            : Sort.Direction.DESC;

        return switch (sortField) {
            case ARRIVAL_DATE_FIELD -> Sort.by(sortDirection, ARRIVAL_DATE_FIELD);
            case LAST_UPDATED_FIELD -> Sort.by(sortDirection, LAST_UPDATED_FIELD);
            default -> defaultSort();
        };
    }

    private static Sort defaultSort() {
        return Sort.by(Sort.Direction.DESC, ARRIVAL_DATE_FIELD);
    }
}
