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
 * EUDPA-390 address lookup spike — dev/local only, see {@link AddressLookupConfig}.
 *
 * <p>{@code oauthTokenUrl} is expected to already carry the tenant (built in YAML via a
 * {@code ${address-lookup.tenant-id}} placeholder), so no URL assembly happens in code.
 *
 * <p>Carries the dev/local {@code @Profile} because {@code @ConfigurationPropertiesScan} would
 * otherwise register it — and then fail validation — in profiles that supply no values.
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
