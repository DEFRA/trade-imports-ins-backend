package uk.gov.defra.trade.imports.ins.backend.configuration;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.aws")
public record AppAwsConfig(
    @Nullable @Pattern(regexp = "^(https?://.*)?$") String endpointOverride,
    @Nullable String accessKeyId,
    @Nullable String secretAccessKey) {
}
