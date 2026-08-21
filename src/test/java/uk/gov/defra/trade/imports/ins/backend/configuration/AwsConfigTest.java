package uk.gov.defra.trade.imports.ins.backend.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

class AwsConfigTest {

    private static final String REGION = "eu-west-2";
    private static final String ENDPOINT = "http://localhost:4566";
    private static final String ACCESS_KEY = "test-access-key";
    private static final String SECRET_KEY = "test-secret-key";

    @Test
    void sqsAsyncClient_shouldBuild_whenNoEndpointOrStaticCredentials() {
        // Given
        AwsConfig awsConfig = new AwsConfig(REGION, new AppAwsConfig(null, null, null));

        // When
        SqsAsyncClient client = awsConfig.sqsAsyncClient();

        // Then
        assertThat(client).isNotNull();
        client.close();
    }

    @Test
    void sqsAsyncClient_shouldBuildWithStaticCredentials_whenEndpointAndBothCredsAreSet() {
        // Given
        AwsConfig awsConfig = new AwsConfig(REGION, new AppAwsConfig(ENDPOINT, ACCESS_KEY, SECRET_KEY));

        // When
        SqsAsyncClient client = awsConfig.sqsAsyncClient();

        // Then
        assertThat(client).isNotNull();
        client.close();
    }

    @Test
    void sqsAsyncClient_shouldFallBackToDefaultCredentials_whenEndpointSetButAccessKeyIsNull() {
        // Given
        AwsConfig awsConfig = new AwsConfig(REGION, new AppAwsConfig(ENDPOINT, null, SECRET_KEY));

        // When
        SqsAsyncClient client = awsConfig.sqsAsyncClient();

        // Then
        assertThat(client).isNotNull();
        client.close();
    }

    @Test
    void sqsAsyncClient_shouldFallBackToDefaultCredentials_whenEndpointSetButSecretKeyIsNull() {
        // Given
        AwsConfig awsConfig = new AwsConfig(REGION, new AppAwsConfig(ENDPOINT, ACCESS_KEY, null));

        // When
        SqsAsyncClient client = awsConfig.sqsAsyncClient();

        // Then
        assertThat(client).isNotNull();
        client.close();
    }

    @Test
    void sqsAsyncClient_shouldFallBackToDefaultCredentials_whenEndpointSetButAccessKeyIsBlank() {
        // Given
        AwsConfig awsConfig = new AwsConfig(REGION, new AppAwsConfig(ENDPOINT, "", SECRET_KEY));

        // When
        SqsAsyncClient client = awsConfig.sqsAsyncClient();

        // Then
        assertThat(client).isNotNull();
        client.close();
    }

    @Test
    void sqsAsyncClient_shouldFallBackToDefaultCredentials_whenEndpointSetButSecretKeyIsBlank() {
        // Given
        AwsConfig awsConfig = new AwsConfig(REGION, new AppAwsConfig(ENDPOINT, ACCESS_KEY, ""));

        // When
        SqsAsyncClient client = awsConfig.sqsAsyncClient();

        // Then
        assertThat(client).isNotNull();
        client.close();
    }
}
