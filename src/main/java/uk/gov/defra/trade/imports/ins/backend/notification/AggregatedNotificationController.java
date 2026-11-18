package uk.gov.defra.trade.imports.ins.backend.notification;

import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.defra.trade.imports.ins.backend.filter.TraderOrganisationHeaderFilter;

@RestController
@RequestMapping("/notifications")
@Tag(name = "Notification API", description = "Read-only query operations over the cross-journey aggregated notification store")
@RequiredArgsConstructor
@Validated
public class AggregatedNotificationController {

    private final AggregatedNotificationQueryService queryService;

    @GetMapping
    @Operation(
        summary = "List notifications",
        description =
            "Returns a paginated list of notifications owned by the organisation in "
                + TraderOrganisationHeaderFilter.ORGANISATION_ID_HEADER
                + ". Optional sort: arrivalDate,desc (default), arrivalDate,asc, lastUpdated,desc, "
                + "lastUpdated,asc. Optional referenceNumber: exact match against a complete "
                + "notification reference.")
    @ApiResponse(responseCode = "200", description = "Paginated notifications returned",
        content = @Content(schema = @Schema(implementation = AggregatedNotificationPageResponse.class)))
    @ApiResponse(
        responseCode = "400",
        description = "Trade-Imports-Organisation-Id header is missing or blank",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @Timed("controller.getAllNotifications.time")
    public AggregatedNotificationPageResponse findAll(
        @RequestParam(defaultValue = "1") @Min(1) int page,
        @RequestParam(required = false) String sort,
        @RequestParam(required = false) String referenceNumber,
        @RequestHeader(
            value = TraderOrganisationHeaderFilter.ORGANISATION_ID_HEADER,
            required = false)
        String organisationId) {
        return queryService.findOrganisationPage(page, sort, referenceNumber, organisationId);
    }
}