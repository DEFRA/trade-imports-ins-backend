package uk.gov.defra.trade.imports.ins.backend.notification;

import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notifications")
@Tag(name = "Notification API", description = "Read-only query operations over the cross-journey aggregated notification store")
@Slf4j
@RequiredArgsConstructor
@Validated
public class AggregatedNotificationController {

    private final AggregatedNotificationQueryService queryService;

    @GetMapping
    @Operation(summary = "List notifications",
        description = "Returns a paginated list of notifications aggregated across every import journey. "
            + "Optional sort: arrivalDate,desc (default), arrivalDate,asc, lastUpdated,desc, lastUpdated,asc. "
            + "Optional referenceNumber: exact match against a complete notification reference.")
    @ApiResponse(responseCode = "200", description = "Paginated notifications returned",
        content = @Content(schema = @Schema(implementation = AggregatedNotificationPageResponse.class)))
    @Timed("controller.getAllNotifications.time")
    public AggregatedNotificationPageResponse findAll(
        @RequestParam(defaultValue = "1") @Min(1) int page,
        @RequestParam(required = false) String sort,
        @RequestParam(required = false) String referenceNumber) {
        log.debug("GET /notifications?page={}&sort={}&referenceNumber={}", page, sort, referenceNumber);
        return queryService.findAll(page, sort, referenceNumber);
    }
}
