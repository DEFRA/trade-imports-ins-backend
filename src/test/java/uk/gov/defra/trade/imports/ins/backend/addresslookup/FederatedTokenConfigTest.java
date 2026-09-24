package uk.gov.defra.trade.imports.ins.backend.addresslookup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.endpoint.OAuth2ClientCredentialsGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.GetWebIdentityTokenRequest;
import software.amazon.awssdk.services.sts.model.GetWebIdentityTokenResponse;

@ExtendWith(MockitoExtension.class)
class FederatedTokenConfigTest {

    private static final String TOKEN_URL = "http://localhost:8087/token";

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

    /**
     * The whole token hop in one call: the parameters converter runs the STS leg, the request
     * interceptor times the Entra response, and the returned lambda reads the access token's
     * claims. Claim logging is deliberately unobservable — the assertion is that a JWT access
     * token survives the round trip, not that a particular line was logged.
     */
    @Test
    void addressLookupTokenResponseClient_shouldMintTheAssertionAndReturnTheEntraToken() {
        // Given
        StsClient stsClient = stsClientReturning("the-assertion-jwt");
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        mockServer.expect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.OK)
                .contentType(MediaType.APPLICATION_JSON)
                .body(tokenResponseBody(jwt("""
                    {"aud":"api://address-lookup","iss":"https://sts.windows.net/tenant/",\
                    "appid":"the-client","azp":"the-client","roles":["Lookup.Read"],"exp":"1790000000"}"""))));

        // When
        OAuth2AccessTokenResponse response = federatedTokenConfig
            .addressLookupTokenResponseClient(stsClient, restClientBuilder, properties(null))
            .getTokenResponse(new OAuth2ClientCredentialsGrantRequest(clientRegistration()));

        // Then
        assertThat(response.getAccessToken().getTokenValue()).startsWith("eyJ");
        mockServer.verify();
    }

    @Test
    void addressLookupTokenResponseClient_shouldStillReturnTheToken_whenItIsNotAJwt() {
        // Given — an opaque token has no payload segment to read claims from
        StsClient stsClient = stsClientReturning("the-assertion-jwt");
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        mockServer.expect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.OK)
                .contentType(MediaType.APPLICATION_JSON)
                .body(tokenResponseBody("an-opaque-token")));

        // When
        OAuth2AccessTokenResponse response = federatedTokenConfig
            .addressLookupTokenResponseClient(stsClient, restClientBuilder, properties(null))
            .getTokenResponse(new OAuth2ClientCredentialsGrantRequest(clientRegistration()));

        // Then
        assertThat(response.getAccessToken().getTokenValue()).isEqualTo("an-opaque-token");
    }

    @Test
    void addressLookupTokenResponseClient_shouldStillReturnTheToken_whenItsClaimsCannotBeRead() {
        // Given — three segments, but the payload is neither base64url nor JSON
        StsClient stsClient = stsClientReturning("the-assertion-jwt");
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        mockServer.expect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.OK)
                .contentType(MediaType.APPLICATION_JSON)
                .body(tokenResponseBody("header.not*valid*base64url.signature")));

        // When
        OAuth2AccessTokenResponse response = federatedTokenConfig
            .addressLookupTokenResponseClient(stsClient, restClientBuilder, properties(null))
            .getTokenResponse(new OAuth2ClientCredentialsGrantRequest(clientRegistration()));

        // Then — claim logging is best-effort and must never fail the token exchange
        assertThat(response.getAccessToken().getTokenValue())
            .isEqualTo("header.not*valid*base64url.signature");
    }

    private static String tokenResponseBody(String accessToken) {
        return """
            {"access_token":"%s","token_type":"Bearer","expires_in":3600}""".formatted(accessToken);
    }

    /** A structurally valid JWT — only the payload segment is ever read. */
    private static String jwt(String claimsJson) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8))
            + "." + encoder.encodeToString(claimsJson.getBytes(StandardCharsets.UTF_8))
            + ".signature-is-never-verified-here";
    }

    @SuppressWarnings("unchecked")
    private static StsClient stsClientReturning(String assertion) {
        StsClient stsClient = mock(StsClient.class);
        when(stsClient.getWebIdentityToken(any(Consumer.class))).thenReturn(
            GetWebIdentityTokenResponse.builder()
                .webIdentityToken(assertion)
                .expiration(Instant.now().plusSeconds(900))
                .build());
        return stsClient;
    }

    private static ClientRegistration clientRegistration() {
        return ClientRegistration.withRegistrationId(FederatedTokenConfig.CLIENT_REGISTRATION_ID)
            .clientId("22222222-2222-2222-2222-222222222222")
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .scope("33333333-3333-3333-3333-333333333333/.default")
            .tokenUri(TOKEN_URL)
            .build();
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
