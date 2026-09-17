package uk.gov.defra.trade.imports.ins.backend.addresslookup;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /address-lookup} for the EUDPA-390 spike page. A real {@code @RestController},
 * unlike {@link AddressLookupClient} — Spring MVC's {@code RequestMappingHandlerMapping} only
 * recognises the {@code @Controller} stereotype as a handler, so this can't be manually instantiated
 * the way {@link AddressLookupConfig} does its other beans — it carries {@link AddressLookupConfig}'s
 * {@code @Profile} itself instead (see that class's Javadoc for why there are two).
 *
 * <p>Iteration 1 takes no parameters and always looks up the configured default postcode; find
 * vs postcode as request parameters is iteration 2.
 */
@RestController
@RequestMapping("/address-lookup")
@Profile({"dev", "local"})
@Tag(name = "Address lookup spike (EUDPA-390, dev/local only)")
@Slf4j
class AddressLookupController {

    private final AddressLookupClient addressLookupClient;
    private final AddressLookupProperties properties;

    AddressLookupController(AddressLookupClient addressLookupClient, AddressLookupProperties properties) {
        this.addressLookupClient = addressLookupClient;
        this.properties = properties;
    }

    @GetMapping
    @Operation(summary = "Look up the configured default postcode against the DEFRA address lookup",
        description = "EUDPA-390 iteration 1 — no query parameters yet; always searches "
            + "the configured default postcode via the federated-credential token chain.")
    AddressLookupResponse lookup() {
        log.debug("GET /address-lookup (default postcode)");
        return addressLookupClient.lookupByPostcode(properties.defaultPostcode());
    }
}
