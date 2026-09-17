package uk.gov.defra.trade.imports.ins.backend.addresslookup;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nullable;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;

/**
 * Maps a DEFRA {@code GET /addresses} HTTP outcome onto {@link AddressLookupResponse}.
 */
@Slf4j
class AddressLookupMapper {

    private static final String REJECTED_POSTCODE_PREFIX = "Requested postcode";

    private final ObjectMapper objectMapper;

    AddressLookupMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    AddressLookupResponse map(
        AddressLookupResponse.Query query, HttpStatusCode status, @Nullable MediaType contentType, String body) {
        int code = status.value();
        if (code == 204 || (code == 400 && rejectedPostcode(body))) {
            return AddressLookupResponse.noResults(query);
        }
        if (status.is2xxSuccessful()) {
            return mapJsonBody(query, contentType, body);
        }
        return AddressLookupResponse.failed(query, failureReason(code));
    }

    private AddressLookupResponse mapJsonBody(
        AddressLookupResponse.Query query, @Nullable MediaType contentType, String body) {
        if (contentType == null || !MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
            return AddressLookupResponse.failed(query, AddressLookupResponse.FailureReason.NON_JSON_200);
        }
        try {
            AddressLookupApiResponse api = objectMapper.readValue(body, AddressLookupApiResponse.class);
            List<AddressLookupResponse.Address> addresses = api.results() == null
                ? List.of()
                : api.results().stream().map(AddressLookupMapper::toAddress).toList();
            return AddressLookupResponse.results(query, addresses, parseTotalResults(api.header()));
        } catch (JsonProcessingException ex) {
            log.warn("Address lookup returned a 200 that did not parse as the expected shape", ex);
            return AddressLookupResponse.failed(query, AddressLookupResponse.FailureReason.NON_JSON_200);
        }
    }

    private boolean rejectedPostcode(String body) {
        try {
            AddressLookupErrorResponse error = objectMapper.readValue(body, AddressLookupErrorResponse.class);
            return error.message() != null && error.message().startsWith(REJECTED_POSTCODE_PREFIX);
        } catch (JsonProcessingException ex) {
            return false;
        }
    }

    private static AddressLookupResponse.Address toAddress(AddressLookupApiResponse.Result result) {
        return new AddressLookupResponse.Address(
            result.addressLine(), result.buildingNumber(), result.buildingName(), result.subBuildingName(),
            result.street(), result.locality(), result.town(), result.postcode(), result.country(), result.uprn(),
            result.match(), result.matchDescription(), result.language(), result.xCoordinate(), result.yCoordinate());
    }

    @Nullable
    private static Integer parseTotalResults(AddressLookupApiResponse.Header header) {
        if (header == null || header.totalResults() == null) {
            return null;
        }
        try {
            return Integer.valueOf(header.totalResults());
        } catch (NumberFormatException _) {
            return null;
        }
    }

    private static AddressLookupResponse.FailureReason failureReason(int status) {
        return switch (status) {
            case 401 -> AddressLookupResponse.FailureReason.HTTP_401;
            case 403 -> AddressLookupResponse.FailureReason.HTTP_403;
            case 500 -> AddressLookupResponse.FailureReason.HTTP_500;
            case 503 -> AddressLookupResponse.FailureReason.HTTP_503;
            default -> AddressLookupResponse.FailureReason.UNKNOWN;
        };
    }
}
