package uk.gov.defra.trade.imports.ins.backend.configuration;

import io.awspring.cloud.sqs.config.SqsMessageListenerContainerFactory;
import java.net.URI;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.retry.RetryMode;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.SqsAsyncClientBuilder;
import uk.gov.defra.trade.imports.ins.backend.notification.NotificationErrorHandler;

@Slf4j
@Configuration
public class AwsConfig {

    private final String region;
    private final AppAwsConfig appAwsConfig;

    public AwsConfig(
            @Value("${aws.region}") String region,
            AppAwsConfig appAwsConfig) {
        this.region = region;
        this.appAwsConfig = appAwsConfig;
    }

    @Bean
    public SqsAsyncClient sqsAsyncClient() {
        SqsAsyncClientBuilder builder = SqsAsyncClient.builder()
            .region(Region.of(region))
            .credentialsProvider(resolveCredentialsProvider())
            .overrideConfiguration(c -> c
                .retryStrategy(RetryMode.ADAPTIVE_V2)
                .apiCallTimeout(Duration.ofSeconds(30))
                .apiCallAttemptTimeout(Duration.ofSeconds(10)));
        if (hasEndpointOverride()) {
            log.info("Using SQS endpoint override: {}", appAwsConfig.endpointOverride());
            builder.endpointOverride(URI.create(appAwsConfig.endpointOverride()));
        }
        return builder.build();
    }

    @Bean
    public SqsMessageListenerContainerFactory<Object> defaultSqsListenerContainerFactory(
            SqsAsyncClient sqsAsyncClient,
            NotificationSqsConfig sqsConfig,
            NotificationErrorHandler errorHandler) {
        return SqsMessageListenerContainerFactory.builder()
            .configure(options -> options
                .maxConcurrentMessages(sqsConfig.maxMessages())
                .maxMessagesPerPoll(sqsConfig.maxMessages())
                .pollTimeout(Duration.ofSeconds(sqsConfig.waitTimeSeconds())))
            .sqsAsyncClient(sqsAsyncClient)
            .errorHandler(errorHandler)
            .build();
    }

    private boolean hasEndpointOverride() {
        return appAwsConfig.endpointOverride() != null
            && !appAwsConfig.endpointOverride().isBlank();
    }

    private boolean hasStaticCredentials() {
        return appAwsConfig.accessKeyId() != null
            && !appAwsConfig.accessKeyId().isBlank()
            && appAwsConfig.secretAccessKey() != null
            && !appAwsConfig.secretAccessKey().isBlank();
    }

    private AwsCredentialsProvider resolveCredentialsProvider() {
        if (hasEndpointOverride() && hasStaticCredentials()) {
            return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(appAwsConfig.accessKeyId(), appAwsConfig.secretAccessKey()));
        }
        if (hasEndpointOverride()) {
            log.warn("APP_AWS_ENDPOINT_OVERRIDE is set but static credentials are absent — falling back to DefaultCredentialsProvider");
        }
        return DefaultCredentialsProvider.builder().build();
    }
}
