package uk.gov.defra.trade.imports.ins.backend.notification;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import uk.gov.defra.trade.imports.ins.backend.filter.TraderOrganisationHeaderFilter;

@WebMvcTest(AggregatedNotificationController.class)
@Import(TraderOrganisationHeaderFilter.class)
class AggregatedNotificationControllerTest {

    private static final String ORGANISATION_ID = "5900001";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AggregatedNotificationQueryService queryService;

    @Test
    void findAll_shouldReturnPageOfNotifications_withDefaultParams() throws Exception {
        when(queryService.findOrganisationPage(1, null, null, ORGANISATION_ID)).thenReturn(
            new AggregatedNotificationPageResponse(Collections.emptyList(), 1, 25, 0, 0, 0));

        mockMvc.perform(listNotificationsRequest())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content").isEmpty())
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.size").value(25))
            .andExpect(jsonPath("$.totalElements").value(0));

        verify(queryService).findOrganisationPage(1, null, null, ORGANISATION_ID);
    }

    @Test
    void findAll_shouldReturn400_whenPageIsZero() throws Exception {
        mockMvc.perform(listNotificationsRequest().param("page", "0"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(queryService);
    }

    @Test
    void findAll_shouldReturn400_whenPageIsNegative() throws Exception {
        mockMvc.perform(listNotificationsRequest().param("page", "-1"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void findAll_shouldPassSortParamToService() throws Exception {
        when(queryService.findOrganisationPage(1, "lastUpdated,asc", null, ORGANISATION_ID)).thenReturn(
            new AggregatedNotificationPageResponse(Collections.emptyList(), 1, 25, 0, 0, 0));

        mockMvc.perform(listNotificationsRequest().param("sort", "lastUpdated,asc"))
            .andExpect(status().isOk());

        verify(queryService).findOrganisationPage(1, "lastUpdated,asc", null, ORGANISATION_ID);
    }

    @Test
    void findAll_shouldPassReferenceNumberParamToService() throws Exception {
        when(queryService.findOrganisationPage(1, null, "GBN-AG-26-001", ORGANISATION_ID)).thenReturn(
            new AggregatedNotificationPageResponse(Collections.emptyList(), 1, 25, 0, 0, 0));

        mockMvc.perform(listNotificationsRequest().param("referenceNumber", "GBN-AG-26-001"))
            .andExpect(status().isOk());

        verify(queryService).findOrganisationPage(1, null, "GBN-AG-26-001", ORGANISATION_ID);
    }

    @Test
    void findAll_shouldReturnBadRequest_whenOrganisationHeaderIsMissing() throws Exception {
        mockMvc.perform(get("/notifications")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.title").value("Bad Request"));

        verifyNoInteractions(queryService);
    }

    @Test
    void findAll_shouldPassPageParamToService() throws Exception {
        when(queryService.findOrganisationPage(2, null, null, ORGANISATION_ID)).thenReturn(
            new AggregatedNotificationPageResponse(Collections.emptyList(), 2, 25, 0, 120, 5));

        mockMvc.perform(listNotificationsRequest().param("page", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(2))
            .andExpect(jsonPath("$.totalElements").value(120))
            .andExpect(jsonPath("$.totalPages").value(5));

        verify(queryService).findOrganisationPage(2, null, null, ORGANISATION_ID);
    }

    private static MockHttpServletRequestBuilder listNotificationsRequest() {
        return get("/notifications")
            .header(TraderOrganisationHeaderFilter.ORGANISATION_ID_HEADER, ORGANISATION_ID)
            .contentType(MediaType.APPLICATION_JSON);
    }
}