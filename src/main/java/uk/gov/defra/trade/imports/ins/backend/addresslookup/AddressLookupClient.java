package uk.gov.defra.trade.imports.ins.backend.addresslookup;

import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import software.amazon.awssdk.core.exception.SdkException;

@Slf4j
class AddressLookupClient {

    private static final int MAX_LOGGED_BODY_LENGTH = 500;

    private final RestClient restClient;
    private final AddressLookupProperties properties;
    private final AddressLookupMapper mapper;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final AddressLookupMetrics metrics;

    AddressLookupClient(
        RestClient restClient,
        AddressLookupProperties properties,
        AddressLookupMapper mapper,
        OAuth2AuthorizedClientService authorizedClientService,
        AddressLookupMetrics metrics) {
        this.restClient = restClient;
        this.properties = properties;
        this.mapper = mapper;
        this.authorizedClientService = authorizedClientService;
        this.metrics = metrics;
    }

    AddressLookupResponse lookupByPostcode(String postcode) {
        return lookup(AddressLookupResponse.Mode.POSTCODE, postcode);
    }

    AddressLookupResponse lookupByFind(String find) {
        return lookup(AddressLookupResponse.Mode.FIND, find);
    }

    /** One place every search returns through, so no path can skip the metrics. */
    private AddressLookupResponse lookup(AddressLookupResponse.Mode mode, String term) {
        AddressLookupResponse response = runSearch(mode, term);
        metrics.record(response);
        return response;
    }

    private AddressLookupResponse runSearch(AddressLookupResponse.Mode mode, String term) {
        AddressLookupResponse.Query query = new AddressLookupResponse.Query(mode, term);
        long start = System.currentTimeMillis();
        LookupTimingsRecorder.start();
        try {
            AddressLookupResponse response = get(query);
            if (response.failureReason() == AddressLookupResponse.FailureReason.HTTP_401) {
                response = retryWithAFreshToken(query);
            }
            log.info("Address lookup for {}={} was {} with {} results in {}ms",
                mode.parameterName(), term, response.outcome(), response.returnedResults(),
                System.currentTimeMillis() - start);
            return response.withTimings(LookupTimingsRecorder.finish());
        } catch (ResourceAccessException ex) {
            log.warn("Address lookup timed out or was unreachable for {}={}", mode.parameterName(), term, ex);
            return AddressLookupResponse.failed(query, AddressLookupResponse.FailureReason.TIMEOUT)
                .withTimings(LookupTimingsRecorder.finish());
        } catch (OAuth2AuthorizationException ex) {
            log.warn("Address lookup token exchange failed for {}={}", mode.parameterName(), term, ex);
            return AddressLookupResponse.failed(query, AddressLookupResponse.FailureReason.TOKEN_FAILED)
                .withTimings(LookupTimingsRecorder.finish());
        } catch (SdkException ex) {
            // The STS hop, which runs inside the token exchange rather than the GET. Without this
            // the whole endpoint answers 500 and says nothing about which of the three hops broke.
            // Running outside the stack with no AWS credentials on the environment is the usual
            // cause: DefaultCredentialsProvider finds nothing to sign GetWebIdentityToken with.
            log.warn("Address lookup could not mint an STS assertion for {}={}", mode.parameterName(), term, ex);
            return AddressLookupResponse.failed(query, AddressLookupResponse.FailureReason.STS_FAILED)
                .withTimings(LookupTimingsRecorder.finish());
        }
    }

    private AddressLookupResponse get(AddressLookupResponse.Query query) {
        // Measured around the whole exchange, which on a cold search includes the token hops the
        // interceptor runs inside it. LookupTimings takes those back off, so what it reports is
        // the gateway's own time either way.
        long start = System.currentTimeMillis();
        try {
            return restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .queryParam(query.mode().parameterName(), query.term())
                    .queryParam("maxresults", properties.maxResults())
                    .build())
                .exchange((request, httpResponse) -> map(query, httpResponse));
        } finally {
            LookupTimingsRecorder.recordLookup(System.currentTimeMillis() - start);
        }
    }

    /**
     * Spring refreshes the token when it expires, but a token can stop being accepted before then —
     * a revoked registration, or a changed gateway policy. The cached one is then handed out on
     * every call until its own expiry, which is an hour of 401s with no token hops in the logs to
     * explain them. Dropping the cached client makes the next call mint a fresh one.
     *
     * <p>Once only, and only for a 401. A second 401 means the token is not the problem — a wrong
     * audience answers exactly the same way — and retrying further would just double every failing
     * call against a service the whole estate shares 300 requests a minute of.
     */
    private AddressLookupResponse retryWithAFreshToken(AddressLookupResponse.Query query) {
        log.warn("Address lookup was refused with 401; evicting the cached token and retrying once");
        authorizedClientService.removeAuthorizedClient(
            FederatedTokenConfig.CLIENT_REGISTRATION_ID, FederatedTokenConfig.CLIENT_REGISTRATION_ID);
        return get(query);
    }

    private AddressLookupResponse map(
        AddressLookupResponse.Query query, RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response)
        throws IOException {
        String body = response.bodyTo(String.class);
        String responseBody = body != null ? body : "";
        if (!response.getStatusCode().is2xxSuccessful()) {
            logRefusal(response.getStatusCode().value(), response.getHeaders(), responseBody);
        }
        return mapper.map(query, response.getStatusCode(), response.getHeaders().getContentType(), responseBody);
    }

    /**
     * The status alone cannot tell a rejected token from a missing subscription key — only the
     * gateway's own words can, and they arrive in the body. Truncated because an API gateway can
     * answer with a whole HTML page.
     */
    private void logRefusal(int status, HttpHeaders headers, String body) {
        log.warn("Address lookup refused the call: status={} wwwAuthenticate={} body={}",
            status,
            headers.getFirst(HttpHeaders.WWW_AUTHENTICATE),
            body.length() > MAX_LOGGED_BODY_LENGTH ? body.substring(0, MAX_LOGGED_BODY_LENGTH) : body);
    }
}
