package uk.gov.defra.trade.imports.ins.backend.configuration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "aws.sqs.notification")
public record NotificationSqsConfig(
    @NotBlank String queueUrl,
    @Min(0) @Max(20) int waitTimeSeconds,
    @Min(1) @Max(10) int maxMessages) {
}
