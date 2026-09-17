package uk.gov.defra.trade.imports.ins.backend.addresslookup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Unit-tests {@link AddressLookupClient} against a mocked lookup endpoint. The
 * token chain itself ({@link FederatedTokenConfigTest}) is replaced with an
 * {@link OAuth2AuthorizedClientManager} stub that hands back a fixed token with no HTTP call.
 *
 * <p>The client is built the same way production is: Spring Security's interceptor lives on
 * the RestClient, so the GET has no OAuth attributes of its own.
 */
class AddressLookupClientTest {

    private static final String LOOKUP_URL = "http://localhost:8087/simulator/address-lookup/v2.1/addresses";

    private final RestClient.Builder restClientBuilder = RestClient.builder();
    private final MockRestServiceServer mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
    private final AddressLookupClient addressLookupClient = client(restClientBuilder, fixedTokenManager());

    @Test
    void lookupByPostcode_shouldReturnResults_forA200WithThreeAddresses() {
        mockServer.expect(method(HttpMethod.GET))
            .andExpect(requestToUriTemplate(LOOKUP_URL + "?postcode={postcode}&maxresults={maxresults}", "SW1A 1AA", 100))
            .andExpect(header("Authorization", "Bearer test-access-token"))
            .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body("""
                {
                  "header": { "totalResults": "3" },
                  "results": [
                    { "addressLine": "1 Downing Street, London, SW1A 2AA", "buildingNumber": "1", "postcode": "SW1A 2AA" },
                    { "addressLine": "Flat A, Downing Street, London, SW1A 2AA", "buildingName": "Flat A", "postcode": "SW1A 2AA" },
                    { "addressLine": "Unit 1, Downing House, Downing Street, London, SW1A 2AA", "subBuildingName": "Unit 1", "buildingName": "Downing House", "postcode": "SW1A 2AA" }
                  ]
                }
                """));

        AddressLookupResponse response = addressLookupClient.lookupByPostcode("SW1A 1AA");

        assertThat(response.outcome()).isEqualTo(AddressLookupResponse.Outcome.RESULTS);
        assertThat(response.query()).isEqualTo(new AddressLookupResponse.Query(AddressLookupResponse.Mode.POSTCODE, "SW1A 1AA"));
        assertThat(response.results()).hasSize(3);
        assertThat(response.results().getFirst().addressLine()).isEqualTo("1 Downing Street, London, SW1A 2AA");
        assertThat(response.results().getFirst().buildingNumber()).isEqualTo("1");
        assertThat(response.results().getFirst().postcode()).isEqualTo("SW1A 2AA");
        assertThat(response.totalResults()).isEqualTo(3);
        assertThat(response.returnedResults()).isEqualTo(3);
        assertThat(response.failureReason()).isNull();
        mockServer.verify();
    }

    @Test
    void lookupByPostcode_shouldReturnNoResults_for204() {
        mockServer.expect(method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.NO_CONTENT));

        AddressLookupResponse response = addressLookupClient.lookupByPostcode("ZZ1 1ZZ");

        assertThat(response.outcome()).isEqualTo(AddressLookupResponse.Outcome.NO_RESULTS);
        assertThat(response.results()).isEmpty();
    }

    @Test
    void lookupByPostcode_shouldReturnNoResults_forARejectedPostcode400() {
        mockServer.expect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                .body("{\"statusCode\":400,\"message\":\"Requested postcode is not valid\"}"));

        AddressLookupResponse response = addressLookupClient.lookupByPostcode("QQ1 1QQ");

        assertThat(response.outcome()).isEqualTo(AddressLookupResponse.Outcome.NO_RESULTS);
    }

    @Test
    void lookupByPostcode_shouldFail_for503() {
        mockServer.expect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).contentType(MediaType.APPLICATION_JSON)
                .body("{\"statusCode\":503,\"message\":\"Service Unavailable\"}"));

        AddressLookupResponse response = addressLookupClient.lookupByPostcode("XX1 1XX");

        assertThat(response.outcome()).isEqualTo(AddressLookupResponse.Outcome.FAILED);
        assertThat(response.failureReason()).isEqualTo(AddressLookupResponse.FailureReason.HTTP_503);
    }

    @Test
    void lookupByPostcode_shouldFail_forANonJson200() {
        mockServer.expect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.TEXT_HTML).body("<html>proxy error</html>"));

        AddressLookupResponse response = addressLookupClient.lookupByPostcode("YY1 1YY");

        assertThat(response.outcome()).isEqualTo(AddressLookupResponse.Outcome.FAILED);
        assertThat(response.failureReason()).isEqualTo(AddressLookupResponse.FailureReason.NON_JSON_200);
    }

    @Test
    void lookupByPostcode_shouldFail_forA400ThatIsNotARejectedPostcode() {
        mockServer.expect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                .body("{\"statusCode\":400,\"message\":\"Rate limit exceeded\"}"));

        AddressLookupResponse response = addressLookupClient.lookupByPostcode("AB1 2CD");

        assertThat(response.outcome()).isEqualTo(AddressLookupResponse.Outcome.FAILED);
        assertThat(response.failureReason()).isEqualTo(AddressLookupResponse.FailureReason.UNKNOWN);
    }

    @Test
    void lookupByPostcode_shouldFail_whenTheLookupTimesOut() {
        RestClient.Builder builder = RestClient.builder()
            .requestFactory((uri, httpMethod) -> {
                throw new ResourceAccessException("I/O error on GET request");
            });
        AddressLookupClient client = client(builder, fixedTokenManager());

        AddressLookupResponse response = client.lookupByPostcode("SW1A 1AA");

        assertThat(response.outcome()).isEqualTo(AddressLookupResponse.Outcome.FAILED);
        assertThat(response.failureReason()).isEqualTo(AddressLookupResponse.FailureReason.TIMEOUT);
    }

    @Test
    void lookupByPostcode_shouldFail_whenTheTokenExchangeFails() {
        OAuth2AuthorizedClientManager failingManager = authorizeRequest -> {
            throw new OAuth2AuthorizationException(new OAuth2Error("invalid_client"));
        };
        AddressLookupClient client = client(RestClient.builder(), failingManager);

        AddressLookupResponse response = client.lookupByPostcode("SW1A 1AA");

        assertThat(response.outcome()).isEqualTo(AddressLookupResponse.Outcome.FAILED);
        assertThat(response.failureReason()).isEqualTo(AddressLookupResponse.FailureReason.TOKEN_FAILED);
    }

    private static AddressLookupClient client(
        RestClient.Builder restClientBuilder, OAuth2AuthorizedClientManager authorizedClientManager) {
        AddressLookupProperties properties = properties();
        return new AddressLookupClient(
            AddressLookupConfig.createAddressLookupRestClient(restClientBuilder, authorizedClientManager, properties),
            properties,
            new AddressLookupMapper(new ObjectMapper()));
    }

    private static AddressLookupProperties properties() {
        return new AddressLookupProperties(
            LOOKUP_URL,
            "http://localhost:8087/simulator/entra/11111111-1111-1111-1111-111111111111/oauth2/v2.0/token",
            "11111111-1111-1111-1111-111111111111",
            "22222222-2222-2222-2222-222222222222",
            "33333333-3333-3333-3333-333333333333/.default",
            "api://AzureADTokenExchange",
            "RS256",
            900,
            100,
            "SW1A 1AA",
            null);
    }

    private static OAuth2AuthorizedClientManager fixedTokenManager() {
        ClientRegistration registration = ClientRegistration.withRegistrationId(FederatedTokenConfig.CLIENT_REGISTRATION_ID)
            .clientId("22222222-2222-2222-2222-222222222222")
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .tokenUri("http://localhost:8087/simulator/entra/token")
            .build();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER, "test-access-token", Instant.now(), Instant.now().plusSeconds(3600));
        OAuth2AuthorizedClient authorizedClient = new OAuth2AuthorizedClient(registration, "system", accessToken);
        return authorizeRequest -> authorizedClient;
    }
}
