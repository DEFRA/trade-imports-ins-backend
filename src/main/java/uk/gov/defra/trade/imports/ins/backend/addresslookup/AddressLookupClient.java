package uk.gov.defra.trade.imports.ins.backend.addresslookup;

import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
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

    AddressLookupClient(
        RestClient restClient, AddressLookupProperties properties, AddressLookupMapper mapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.mapper = mapper;
    }

    AddressLookupResponse lookupByPostcode(String postcode) {
        AddressLookupResponse.Query query =
            new AddressLookupResponse.Query(AddressLookupResponse.Mode.POSTCODE, postcode);
        long start = System.currentTimeMillis();
        try {
            AddressLookupResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .queryParam("postcode", postcode)
                    .queryParam("maxresults", properties.maxResults())
                    .build())
                .exchange((request, httpResponse) -> map(query, httpResponse));
            log.info("Address lookup for postcode={} was {} with {} results in {}ms",
                postcode, response.outcome(), response.returnedResults(), System.currentTimeMillis() - start);
            return response;
        } catch (ResourceAccessException ex) {
            log.warn("Address lookup timed out or was unreachable for postcode={}", postcode, ex);
            return AddressLookupResponse.failed(query, AddressLookupResponse.FailureReason.TIMEOUT);
        } catch (OAuth2AuthorizationException ex) {
            log.warn("Address lookup token exchange failed for postcode={}", postcode, ex);
            return AddressLookupResponse.failed(query, AddressLookupResponse.FailureReason.TOKEN_FAILED);
        } catch (SdkException ex) {
            // The STS hop, which runs inside the token exchange rather than the GET. Without this
            // the whole endpoint answers 500 and says nothing about which of the three hops broke.
            // Running outside the stack with no AWS credentials on the environment is the usual
            // cause: DefaultCredentialsProvider finds nothing to sign GetWebIdentityToken with.
            log.warn("Address lookup could not mint an STS assertion for postcode={}", postcode, ex);
            return AddressLookupResponse.failed(query, AddressLookupResponse.FailureReason.STS_FAILED);
        }
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
