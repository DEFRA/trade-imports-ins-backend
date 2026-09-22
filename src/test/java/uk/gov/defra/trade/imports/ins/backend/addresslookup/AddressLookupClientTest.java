package uk.gov.defra.trade.imports.ins.backend.addresslookup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import software.amazon.awssdk.core.exception.SdkClientException;

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

    private static final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

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
                    { "addressLine": "1 DOWNING STREET, LONDON, SW1A 2AA", "buildingNumber": "1", "postcode": "SW1A 2AA" },
                    { "addressLine": "FLAT A, DOWNING STREET, LONDON, SW1A 2AA", "buildingName": "FLAT A", "postcode": "SW1A 2AA" },
                    { "addressLine": "UNIT 1, DOWNING HOUSE, DOWNING STREET, LONDON, SW1A 2AA", "subBuildingName": "UNIT 1", "buildingName": "DOWNING HOUSE", "postcode": "SW1A 2AA" }
                  ]
                }
                """));

        AddressLookupResponse response = addressLookupClient.lookupByPostcode("SW1A 1AA");

        assertThat(response.outcome()).isEqualTo(AddressLookupResponse.Outcome.RESULTS);
        assertThat(response.query()).isEqualTo(new AddressLookupResponse.Query(AddressLookupResponse.Mode.POSTCODE, "SW1A 1AA"));
        assertThat(response.results()).hasSize(3);
        assertThat(response.results().getFirst().addressLine()).isEqualTo("1 DOWNING STREET, LONDON, SW1A 2AA");
        assertThat(response.results().getFirst().buildingNumber()).isEqualTo("1");
        assertThat(response.results().getFirst().postcode()).isEqualTo("SW1A 2AA");
        assertThat(response.totalResults()).isEqualTo(3);
        assertThat(response.returnedResults()).isEqualTo(3);
        assertThat(response.failureReason()).isNull();
        mockServer.verify();
    }

    @Test
    void lookupByPostcode_shouldReportTheTimeTheSearchTook() {
        mockServer.expect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body("""
                { "header": { "totalResults": "0" }, "results": [] }
                """));

        AddressLookupResponse response = addressLookupClient.lookupByPostcode("SW1A 1AA");

        // The token manager here is a stub that mints nothing, so this is the cached shape.
        assertThat(response.timings()).isNotNull();
        assertThat(response.timings().tokenSource()).isEqualTo(LookupTimings.TokenSource.CACHED);
        assertThat(response.timings().lookupMs()).isGreaterThanOrEqualTo(0);
        assertThat(response.timings().totalMs()).isGreaterThanOrEqualTo(response.timings().lookupMs());
    }

    @Test
    void lookupByPostcode_shouldReportTimings_evenWhenTheSearchFails() {
        mockServer.expect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).contentType(MediaType.APPLICATION_JSON)
                .body("{}"));

        AddressLookupResponse response = addressLookupClient.lookupByPostcode("XX1 1XX");

        assertThat(response.outcome()).isEqualTo(AddressLookupResponse.Outcome.FAILED);
        assertThat(response.timings()).isNotNull();
    }

    @Test
    void lookupByFind_shouldSendFindRatherThanPostcode() {
        mockServer.expect(method(HttpMethod.GET))
            .andExpect(requestToUriTemplate(LOOKUP_URL + "?find={find}&maxresults={maxresults}", "Buckingham Palace", 100))
            .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body("""
                { "header": { "totalResults": "1" }, "results": [ { "addressLine": "BUCKINGHAM PALACE, LONDON, SW1A 1AA" } ] }
                """));

        AddressLookupResponse response = addressLookupClient.lookupByFind("Buckingham Palace");

        assertThat(response.outcome()).isEqualTo(AddressLookupResponse.Outcome.RESULTS);
        assertThat(response.query())
            .isEqualTo(new AddressLookupResponse.Query(AddressLookupResponse.Mode.FIND, "Buckingham Palace"));
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

    @Test
    void lookupByPostcode_shouldEvictTheCachedTokenAndSucceed_whenTheFirstCallIs401() {
        OAuth2AuthorizedClientService authorizedClientService = authorizedClientService();
        authorizedClientService.saveAuthorizedClient(authorizedClient(), principal());
        mockServer.expect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                .body("{ \"statusCode\": 401, \"message\": \"Unauthorized. Access token is missing or invalid.\" }"));
        mockServer.expect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body("""
                { "header": { "totalResults": "1" }, "results": [ { "addressLine": "BUCKINGHAM PALACE, LONDON, SW1A 1AA" } ] }
                """));
        AddressLookupClient client = client(restClientBuilder, fixedTokenManager(), authorizedClientService);

        AddressLookupResponse response = client.lookupByPostcode("SW1A 1AA");

        assertThat(response.outcome()).isEqualTo(AddressLookupResponse.Outcome.RESULTS);
        OAuth2AuthorizedClient cached = authorizedClientService.loadAuthorizedClient(
            FederatedTokenConfig.CLIENT_REGISTRATION_ID, FederatedTokenConfig.CLIENT_REGISTRATION_ID);
        assertThat(cached).isNull();
        mockServer.verify();
    }

    @Test
    void lookupByPostcode_shouldRetryOnlyOnce_whenTheSecondCallIsAlso401() {
        // A second 401 is not a stale token — a wrong audience answers identically, and the whole
        // estate shares 300 requests a minute.
        mockServer.expect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON).body("{}"));
        mockServer.expect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON).body("{}"));

        AddressLookupResponse response = addressLookupClient.lookupByPostcode("SW1A 1AA");

        assertThat(response.outcome()).isEqualTo(AddressLookupResponse.Outcome.FAILED);
        assertThat(response.failureReason()).isEqualTo(AddressLookupResponse.FailureReason.HTTP_401);
        mockServer.verify();
    }

    @Test
    void lookupByPostcode_shouldFail_whenTheStsAssertionCannotBeMinted() {
        // The STS hop runs inside the token exchange. Its failure used to escape as a 500 that
        // said nothing about which hop broke; the usual cause is a native run with no AWS
        // credentials on the environment.
        OAuth2AuthorizedClientManager failingManager = authorizeRequest -> {
            throw SdkClientException.create("Unable to load credentials from any of the providers in the chain");
        };
        AddressLookupClient client = client(RestClient.builder(), failingManager);

        AddressLookupResponse response = client.lookupByPostcode("SW1A 1AA");

        assertThat(response.outcome()).isEqualTo(AddressLookupResponse.Outcome.FAILED);
        assertThat(response.failureReason()).isEqualTo(AddressLookupResponse.FailureReason.STS_FAILED);
    }

    private static AddressLookupClient client(
        RestClient.Builder restClientBuilder, OAuth2AuthorizedClientManager authorizedClientManager) {
        return client(restClientBuilder, authorizedClientManager, authorizedClientService());
    }

    private static AddressLookupClient client(
        RestClient.Builder restClientBuilder,
        OAuth2AuthorizedClientManager authorizedClientManager,
        OAuth2AuthorizedClientService authorizedClientService) {
        AddressLookupProperties properties = properties();
        return new AddressLookupClient(
            AddressLookupConfig.createAddressLookupRestClient(restClientBuilder, authorizedClientManager, properties),
            properties,
            new AddressLookupMapper(new ObjectMapper()),
            authorizedClientService,
            new AddressLookupMetrics(meterRegistry));
    }

    private static OAuth2AuthorizedClientService authorizedClientService() {
        return new InMemoryOAuth2AuthorizedClientService(registrationId -> registration());
    }

    /** The same application-scoped principal {@link AddressLookupConfig} resolves for every call. */
    private static Authentication principal() {
        return new AnonymousAuthenticationToken(
            FederatedTokenConfig.CLIENT_REGISTRATION_ID,
            FederatedTokenConfig.CLIENT_REGISTRATION_ID,
            AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
    }

    private static OAuth2AuthorizedClient authorizedClient() {
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER, "stale-access-token", Instant.now(), Instant.now().plusSeconds(3600));
        return new OAuth2AuthorizedClient(
            registration(), FederatedTokenConfig.CLIENT_REGISTRATION_ID, accessToken);
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

    private static ClientRegistration registration() {
        return ClientRegistration.withRegistrationId(FederatedTokenConfig.CLIENT_REGISTRATION_ID)
            .clientId("22222222-2222-2222-2222-222222222222")
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .tokenUri("http://localhost:8087/simulator/entra/token")
            .build();
    }

    private static OAuth2AuthorizedClientManager fixedTokenManager() {
        ClientRegistration registration = registration();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER, "test-access-token", Instant.now(), Instant.now().plusSeconds(3600));
        OAuth2AuthorizedClient authorizedClient = new OAuth2AuthorizedClient(registration, "system", accessToken);
        return authorizeRequest -> authorizedClient;
    }
}
