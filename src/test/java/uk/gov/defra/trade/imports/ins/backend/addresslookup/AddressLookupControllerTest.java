package uk.gov.defra.trade.imports.ins.backend.addresslookup;

import static org.mockito.Mockito.verify;
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
    void lookup_shouldSearchTheConfiguredDefaultPostcode_withNoParameters() throws Exception {
        // Given
        AddressLookupResponse expected = AddressLookupResponse.results(
            new AddressLookupResponse.Query(AddressLookupResponse.Mode.POSTCODE, "SW1A 1AA"),
            List.of(new AddressLookupResponse.Address(
                "1 Downing Street, London, SW1A 2AA",
                "1",
                null,
                null,
                "Downing Street",
                null,
                "London",
                "SW1A 2AA",
                "England",
                "100023336901",
                "1.0",
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
            .andExpect(jsonPath("$.results[0].addressLine").value("1 Downing Street, London, SW1A 2AA"))
            .andExpect(jsonPath("$.totalResults").value(3))
            .andExpect(jsonPath("$.returnedResults").value(1));

        verify(addressLookupClient).lookupByPostcode("SW1A 1AA");
    }
}
