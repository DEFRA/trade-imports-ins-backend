package uk.gov.defra.trade.imports.ins.backend.addresslookup;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.validation.annotation.Validated;

/**
 * Address lookup settings, bound in the {@code dev} and {@code local} profiles only.
 * {@code oauthTokenUrl} is the full token URL, tenant included.
 */
@Validated
@Profile({"dev", "local"})
@ConfigurationProperties(prefix = "address-lookup")
public record AddressLookupProperties(
    @NotBlank String apiUrl,
    @NotBlank String oauthTokenUrl,
    @NotBlank String tenantId,
    @NotBlank String clientId,
    @NotBlank String clientScope,
    @NotBlank String audience,
    @NotBlank String signingAlgorithm,
    @Positive @Max(900) int assertionDurationSeconds,
    @Positive @Max(100) int maxResults,
    @NotBlank String defaultPostcode,
    @Nullable @Pattern(regexp = "^(https?://.*)?$") String stsEndpointOverride) {
}
