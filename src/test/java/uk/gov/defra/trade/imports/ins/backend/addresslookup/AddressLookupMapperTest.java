package uk.gov.defra.trade.imports.ins.backend.addresslookup;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * Unit-tests {@link AddressLookupMapper} directly, for the lookup's own quirks — the responses it
 * gives that its published specification does not describe, and the ones only a proxy produces.
 */
class AddressLookupMapperTest {

    private static final AddressLookupResponse.Query QUERY =
        new AddressLookupResponse.Query(AddressLookupResponse.Mode.POSTCODE, "SW1A 1AA");

    private final AddressLookupMapper mapper = new AddressLookupMapper(new ObjectMapper());

    @Test
    void map_shouldReturnNoResults_for204() {
        AddressLookupResponse response = mapper.map(QUERY, HttpStatus.NO_CONTENT, null, "");

        assertThat(response.outcome()).isEqualTo(AddressLookupResponse.Outcome.NO_RESULTS);
        assertThat(response.results()).isEmpty();
        assertThat(response.failureReason()).isNull();
    }

    @Test
    void map_shouldReturnNoResults_for400RejectingThePostcode() {
        String body = """
            { "statusCode": 400, "message": "Requested postcode must contain a minimum of the sector plus 1 digit" }
            """;

        AddressLookupResponse response = mapper.map(QUERY, HttpStatus.BAD_REQUEST, MediaType.APPLICATION_JSON, body);

        assertThat(response.outcome()).isEqualTo(AddressLookupResponse.Outcome.NO_RESULTS);
    }

    @Test
    void map_shouldReturnFailed_for400WithADifferentMessage() {
        String body = """
            { "statusCode": 400, "message": "Bad Request Error Message" }
            """;

        AddressLookupResponse response = mapper.map(QUERY, HttpStatus.BAD_REQUEST, MediaType.APPLICATION_JSON, body);

        assertThat(response.outcome()).isEqualTo(AddressLookupResponse.Outcome.FAILED);
        assertThat(response.failureReason()).isEqualTo(AddressLookupResponse.FailureReason.UNKNOWN);
    }

    @Test
    void map_shouldReturnFailed_for400WhoseBodyIsNotJson() {
        AddressLookupResponse response =
            mapper.map(QUERY, HttpStatus.BAD_REQUEST, MediaType.TEXT_HTML, "<html>Bad Request</html>");

        assertThat(response.outcome()).isEqualTo(AddressLookupResponse.Outcome.FAILED);
        assertThat(response.failureReason()).isEqualTo(AddressLookupResponse.FailureReason.UNKNOWN);
    }

    @Test
    void map_shouldReturnFailed_for200WithNoContentType() {
        AddressLookupResponse response = mapper.map(QUERY, HttpStatus.OK, null, "{}");

        assertThat(response.failureReason()).isEqualTo(AddressLookupResponse.FailureReason.NON_JSON_200);
    }

    @Test
    void map_shouldReturnFailed_for200ThatIsAnHtmlErrorPage() {
        AddressLookupResponse response =
            mapper.map(QUERY, HttpStatus.OK, MediaType.TEXT_HTML, "<html>Gateway error</html>");

        assertThat(response.failureReason()).isEqualTo(AddressLookupResponse.FailureReason.NON_JSON_200);
    }

    @Test
    void map_shouldReturnFailed_for200WhoseJsonIsMalformed() {
        AddressLookupResponse response = mapper.map(QUERY, HttpStatus.OK, MediaType.APPLICATION_JSON, "{ not json");

        assertThat(response.failureReason()).isEqualTo(AddressLookupResponse.FailureReason.NON_JSON_200);
    }

    @Test
    void map_shouldReturnNoResults_for200CarryingNoResultsArray() {
        AddressLookupResponse response =
            mapper.map(QUERY, HttpStatus.OK, MediaType.APPLICATION_JSON, """
                { "header": { "totalResults": "0" } }
                """);

        assertThat(response.outcome()).isEqualTo(AddressLookupResponse.Outcome.NO_RESULTS);
        assertThat(response.results()).isEmpty();
    }

    @Test
    void map_shouldLeaveTotalResultsNull_whenTheHeaderIsAbsent() {
        AddressLookupResponse response =
            mapper.map(QUERY, HttpStatus.OK, MediaType.APPLICATION_JSON, """
                { "results": [ { "addressLine": "10 Downing Street, London, SW1A 2AA" } ] }
                """);

        assertThat(response.outcome()).isEqualTo(AddressLookupResponse.Outcome.RESULTS);
        assertThat(response.totalResults()).isNull();
        assertThat(response.returnedResults()).isEqualTo(1);
    }

    /** The lookup sends the header counts as strings, so a non-numeric one is possible. */
    @Test
    void map_shouldLeaveTotalResultsNull_whenItIsNotANumber() {
        AddressLookupResponse response =
            mapper.map(QUERY, HttpStatus.OK, MediaType.APPLICATION_JSON, """
                { "header": { "totalResults": "many" }, "results": [ { "addressLine": "10 Downing Street" } ] }
                """);

        assertThat(response.totalResults()).isNull();
    }

    @Test
    void map_shouldCarryEveryAddressFieldThrough() {
        AddressLookupResponse response =
            mapper.map(QUERY, HttpStatus.OK, MediaType.APPLICATION_JSON, """
                {
                  "header": { "totalResults": "1" },
                  "results": [
                    {
                      "addressLine": "Unit 1, Downing House, 10 Downing Street, London, SW1A 2AA",
                      "buildingNumber": "10",
                      "buildingName": "Downing House",
                      "subBuildingName": "Unit 1",
                      "street": "Downing Street",
                      "locality": "Westminster",
                      "town": "London",
                      "postcode": "SW1A 2AA",
                      "country": "England",
                      "uprn": "100023336956",
                      "match": "1.0",
                      "matchDescription": "EXACT",
                      "language": "EN",
                      "xCoordinate": 530047,
                      "yCoordinate": 179951
                    }
                  ]
                }
                """);

        assertThat(response.totalResults()).isEqualTo(1);
        AddressLookupResponse.Address address = response.results().getFirst();
        assertThat(address.addressLine()).isEqualTo("Unit 1, Downing House, 10 Downing Street, London, SW1A 2AA");
        assertThat(address.buildingNumber()).isEqualTo("10");
        assertThat(address.buildingName()).isEqualTo("Downing House");
        assertThat(address.subBuildingName()).isEqualTo("Unit 1");
        assertThat(address.street()).isEqualTo("Downing Street");
        assertThat(address.locality()).isEqualTo("Westminster");
        assertThat(address.town()).isEqualTo("London");
        assertThat(address.postcode()).isEqualTo("SW1A 2AA");
        assertThat(address.country()).isEqualTo("England");
        assertThat(address.uprn()).isEqualTo("100023336956");
        assertThat(address.match()).isEqualTo("1.0");
        assertThat(address.matchDescription()).isEqualTo("EXACT");
        assertThat(address.language()).isEqualTo("EN");
        assertThat(address.xCoordinate()).isEqualTo(530047);
        assertThat(address.yCoordinate()).isEqualTo(179951);
    }

    @Test
    void map_shouldNameTheFailure_forEachStatusWeExpectFromTheGateway() {
        assertThat(mapper.map(QUERY, HttpStatus.UNAUTHORIZED, null, "").failureReason())
            .isEqualTo(AddressLookupResponse.FailureReason.HTTP_401);
        assertThat(mapper.map(QUERY, HttpStatus.FORBIDDEN, null, "").failureReason())
            .isEqualTo(AddressLookupResponse.FailureReason.HTTP_403);
        assertThat(mapper.map(QUERY, HttpStatus.INTERNAL_SERVER_ERROR, null, "").failureReason())
            .isEqualTo(AddressLookupResponse.FailureReason.HTTP_500);
        // The lookup reports throttling as 503, not 429.
        assertThat(mapper.map(QUERY, HttpStatus.SERVICE_UNAVAILABLE, null, "").failureReason())
            .isEqualTo(AddressLookupResponse.FailureReason.HTTP_503);
        assertThat(mapper.map(QUERY, HttpStatus.NOT_FOUND, null, "").failureReason())
            .isEqualTo(AddressLookupResponse.FailureReason.UNKNOWN);
    }
}
