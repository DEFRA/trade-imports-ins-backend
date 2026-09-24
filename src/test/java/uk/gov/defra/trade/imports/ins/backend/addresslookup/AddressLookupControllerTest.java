package uk.gov.defra.trade.imports.ins.backend.addresslookup;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AddressLookupController.class)
@ActiveProfiles("local")
@EnableConfigurationProperties(AddressLookupProperties.class)
@TestPropertySource(properties = {
    "address-lookup.api-url=http://localhost:8087/addresses",
    "address-lookup.oauth-token-url=http://localhost:8087/token",
    "address-lookup.tenant-id=11111111-1111-1111-1111-111111111111",
    "address-lookup.client-id=22222222-2222-2222-2222-222222222222",
    "address-lookup.client-scope=33333333-3333-3333-3333-333333333333/.default",
    "address-lookup.audience=api://AzureADTokenExchange",
    "address-lookup.signing-algorithm=RS256",
    "address-lookup.assertion-duration-seconds=900",
    "address-lookup.max-results=100",
    "address-lookup.default-postcode=SW1A 1AA"
})
class AddressLookupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AddressLookupClient addressLookupClient;

    @Test
    void lookup_shouldSearchTheGivenPostcode() throws Exception {
        AddressLookupResponse expected = AddressLookupResponse.noResults(
            new AddressLookupResponse.Query(AddressLookupResponse.Mode.POSTCODE, "SW1A 2AA"));
        when(addressLookupClient.lookupByPostcode("SW1A 2AA")).thenReturn(expected);

        mockMvc.perform(get("/address-lookup").param("postcode", "SW1A 2AA"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.query.mode").value("POSTCODE"))
            .andExpect(jsonPath("$.query.term").value("SW1A 2AA"));

        verify(addressLookupClient).lookupByPostcode("SW1A 2AA");
    }

    @Test
    void lookup_shouldSearchByFind_whenFindIsGiven() throws Exception {
        AddressLookupResponse expected = AddressLookupResponse.noResults(
            new AddressLookupResponse.Query(AddressLookupResponse.Mode.FIND, "Buckingham Palace"));
        when(addressLookupClient.lookupByFind("Buckingham Palace")).thenReturn(expected);

        mockMvc.perform(get("/address-lookup").param("find", "Buckingham Palace"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.query.mode").value("FIND"))
            .andExpect(jsonPath("$.query.term").value("Buckingham Palace"));

        verify(addressLookupClient).lookupByFind("Buckingham Palace");
    }

    @Test
    void lookup_shouldRejectBothParametersAtOnce() throws Exception {
        // One call can only be one of them, and answering with the postcode result would look
        // like find behaves the same way — the question the spike exists to settle.
        mockMvc.perform(get("/address-lookup")
                .param("postcode", "SW1A 1AA")
                .param("find", "Buckingham Palace"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(addressLookupClient);
    }

    @Test
    void lookup_shouldSearchTheConfiguredDefaultPostcode_withNoParameters() throws Exception {
        // Given
        AddressLookupResponse expected = AddressLookupResponse.results(
            new AddressLookupResponse.Query(AddressLookupResponse.Mode.POSTCODE, "SW1A 1AA"),
            List.of(new AddressLookupResponse.Address(
                "1 DOWNING STREET, LONDON, SW1A 2AA",
                "1",
                null,
                null,
                "DOWNING STREET",
                null,
                "LONDON",
                "SW1A 2AA",
                "ENGLAND",
                "100023336901",
                "1",
                "EXACT",
                "EN",
                null,
                null)),
            3);
        when(addressLookupClient.lookupByPostcode("SW1A 1AA")).thenReturn(expected);

        // When & Then
        mockMvc.perform(get("/address-lookup").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.outcome").value("RESULTS"))
            .andExpect(jsonPath("$.query.mode").value("POSTCODE"))
            .andExpect(jsonPath("$.query.term").value("SW1A 1AA"))
            .andExpect(jsonPath("$.results[0].addressLine").value("1 DOWNING STREET, LONDON, SW1A 2AA"))
            .andExpect(jsonPath("$.totalResults").value(3))
            .andExpect(jsonPath("$.returnedResults").value(1));

        verify(addressLookupClient).lookupByPostcode("SW1A 1AA");
    }
}
