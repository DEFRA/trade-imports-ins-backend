package uk.gov.defra.trade.imports.ins.backend.addresslookup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Instant;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.util.MultiValueMap;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.GetWebIdentityTokenRequest;
import software.amazon.awssdk.services.sts.model.GetWebIdentityTokenResponse;

@ExtendWith(MockitoExtension.class)
class FederatedTokenConfigTest {

    private final FederatedTokenConfig federatedTokenConfig = new FederatedTokenConfig();

    @Test
    void addressLookupStsClient_shouldBuild_whenNoEndpointOverride() {
        // Given
        // When
        StsClient client = federatedTokenConfig.addressLookupStsClient("eu-west-2", properties(null));

        // Then
        assertThat(client.serviceClientConfiguration().endpointOverride()).isEmpty();
        client.close();
    }

    @Test
    void addressLookupStsClient_shouldBuild_whenEndpointOverrideSet() {
        // Given
        URI override = URI.create("http://localhost:8087/simulator/sts");

        // When
        StsClient client = federatedTokenConfig.addressLookupStsClient("eu-west-2", properties(override.toString()));

        // Then
        assertThat(client.serviceClientConfiguration().endpointOverride()).contains(override);
        client.close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void clientAssertionParameters_shouldCallStsWithConfiguredAudienceAndAlgorithm_andAttachTheAssertion() {
        // Given
        StsClient stsClient = mock(StsClient.class);
        GetWebIdentityTokenResponse response = GetWebIdentityTokenResponse.builder()
            .webIdentityToken("the-assertion-jwt")
            .expiration(Instant.now().plusSeconds(900))
            .build();
        when(stsClient.getWebIdentityToken(any(Consumer.class))).thenAnswer(invocation -> {
            Consumer<GetWebIdentityTokenRequest.Builder> consumer = invocation.getArgument(0);
            GetWebIdentityTokenRequest.Builder builder = GetWebIdentityTokenRequest.builder();
            consumer.accept(builder);
            GetWebIdentityTokenRequest request = builder.build();
            assertThat(request.audience()).containsExactly("api://AzureADTokenExchange");
            assertThat(request.signingAlgorithm()).isEqualTo("RS256");
            assertThat(request.durationSeconds()).isEqualTo(900);
            return response;
        });

        // When
        MultiValueMap<String, String> parameters = federatedTokenConfig.clientAssertionParameters(stsClient, properties(null));

        // Then
        assertThat(parameters.getFirst(OAuth2ParameterNames.CLIENT_ASSERTION_TYPE))
            .isEqualTo("urn:ietf:params:oauth:client-assertion-type:jwt-bearer");
        assertThat(parameters.getFirst(OAuth2ParameterNames.CLIENT_ASSERTION)).isEqualTo("the-assertion-jwt");
    }

    private static AddressLookupProperties properties(String stsEndpointOverride) {
        return new AddressLookupProperties(
            "http://localhost:8087/addresses",
            "http://localhost:8087/token",
            "11111111-1111-1111-1111-111111111111",
            "22222222-2222-2222-2222-222222222222",
            "33333333-3333-3333-3333-333333333333/.default",
            "api://AzureADTokenExchange",
            "RS256",
            900,
            100,
            "SW1A 1AA",
            stsEndpointOverride);
    }
}
