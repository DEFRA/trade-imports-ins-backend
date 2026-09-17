package uk.gov.defra.trade.imports.ins.backend.addresslookup;

import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Slf4j
class AddressLookupClient {

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
        try {
            return restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .queryParam("postcode", postcode)
                    .queryParam("maxresults", properties.maxResults())
                    .build())
                .exchange((request, response) -> map(query, response));
        } catch (ResourceAccessException ex) {
            log.warn("Address lookup timed out or was unreachable for postcode={}", postcode, ex);
            return AddressLookupResponse.failed(query, AddressLookupResponse.FailureReason.TIMEOUT);
        } catch (OAuth2AuthorizationException ex) {
            log.warn("Address lookup token exchange failed for postcode={}", postcode, ex);
            return AddressLookupResponse.failed(query, AddressLookupResponse.FailureReason.TOKEN_FAILED);
        }
    }

    private AddressLookupResponse map(
        AddressLookupResponse.Query query, RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response)
        throws IOException {
        String body = response.bodyTo(String.class);
        return mapper.map(query, response.getStatusCode(), response.getHeaders().getContentType(),
            body != null ? body : "");
    }
}
