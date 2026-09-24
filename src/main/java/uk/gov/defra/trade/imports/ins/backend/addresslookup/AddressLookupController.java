package uk.gov.defra.trade.imports.ins.backend.addresslookup;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@code GET /address-lookup} for the EUDPA-390 spike page. A real {@code @RestController},
 * unlike {@link AddressLookupClient} — Spring MVC's {@code RequestMappingHandlerMapping} only
 * recognises the {@code @Controller} stereotype as a handler, so this can't be manually instantiated
 * the way {@link AddressLookupConfig} does its other beans — it carries {@link AddressLookupConfig}'s
 * {@code @Profile} itself instead (see that class's Javadoc for why there are two).
 *
 * <p>Takes either {@code postcode} or {@code find}, and with neither searches the configured
 * default postcode — which is what the spike page's one-button first search does.
 */
@RestController
@RequestMapping("/address-lookup")
@Profile({"dev", "local"})
@Validated
@Tag(name = "Address lookup spike (EUDPA-390, dev/local only)")
@Slf4j
class AddressLookupController {

    /** Longer than any real postcode or address fragment, and short enough not to be a payload. */
    private static final int MAX_TERM_LENGTH = 200;

    private final AddressLookupClient addressLookupClient;
    private final AddressLookupProperties properties;

    AddressLookupController(AddressLookupClient addressLookupClient, AddressLookupProperties properties) {
        this.addressLookupClient = addressLookupClient;
        this.properties = properties;
    }

    @GetMapping
    @Operation(summary = "Search the DEFRA address lookup by postcode or by find",
        description = "Searches via the federated-credential token chain. Give either postcode or "
            + "find, not both. Neither searches the configured default postcode, which is what the "
            + "spike page's first button does.")
    AddressLookupResponse lookup(
        @RequestParam(required = false) @Size(max = MAX_TERM_LENGTH) String postcode,
        @RequestParam(required = false) @Size(max = MAX_TERM_LENGTH) String find) {
        if (StringUtils.hasText(postcode) && StringUtils.hasText(find)) {
            // The point of the page is to compare the two, and a single call can only be one of
            // them. Answering with the postcode result would silently look like find works the
            // same way, which is the very question the spike is here to settle.
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Give either postcode or find, not both");
        }
        // info, not debug: deployed environments run at info, and a spike nobody can see the start
        // of is hard to tell apart from one that never ran.
        if (StringUtils.hasText(find)) {
            log.info("GET /address-lookup, searching find={}", find);
            return addressLookupClient.lookupByFind(find);
        }
        String term = StringUtils.hasText(postcode) ? postcode : properties.defaultPostcode();
        log.info("GET /address-lookup, searching postcode={}", term);
        return addressLookupClient.lookupByPostcode(term);
    }

    /**
     * Kept local to this controller. {@code GlobalExceptionHandler} catches
     * {@code RuntimeException}, and {@link ResponseStatusException} is one, so without this the
     * 400 above is reported as a 500. A controller-local handler wins over the {@code
     * @ControllerAdvice} one and leaves every other endpoint's behaviour alone, which suits a
     * spike that is meant to be deleted whole.
     */
    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail handleBadRequest(ResponseStatusException ex) {
        ProblemDetail problemDetail =
            ProblemDetail.forStatusAndDetail(ex.getStatusCode(), ex.getReason());
        problemDetail.setTitle("Bad Request");
        return problemDetail;
    }
}
