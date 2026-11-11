package uk.gov.defra.trade.imports.ins.backend.notification;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.defra.trade.imports.ins.backend.filter.OrganisationHeaderFilter;

@WebMvcTest(AggregatedNotificationController.class)
class AggregatedNotificationControllerTest {

    private static final String ORGANISATION_ID = "5900001";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AggregatedNotificationQueryService queryService;

  @Test
    void findAll_shouldReturnPageOfNotifications_withDefaultParams() throws Exception {
        when(queryService.findAll(1, null, null, ORGANISATION_ID)).thenReturn(
            new AggregatedNotificationPageResponse(Collections.emptyList(), 1, 25, 0, 0, 0));

        mockMvc.perform(get("/notifications")
                .header(OrganisationHeaderFilter.ORGANISATION_ID_HEADER, ORGANISATION_ID)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content").isEmpty())
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.size").value(25))
            .andExpect(jsonPath("$.totalElements").value(0));

        verify(queryService).findAll(1, null, null, ORGANISATION_ID);
    }

    @Test
    void findAll_shouldReturn400_whenPageIsZero() throws Exception {
        mockMvc.perform(get("/notifications")
                .header(OrganisationHeaderFilter.ORGANISATION_ID_HEADER, ORGANISATION_ID)
                .param("page", "0")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest());

        verify(queryService, never()).findAll(eq(0), isNull(), isNull(), eq(ORGANISATION_ID));
    }

    @Test
    void findAll_shouldReturn400_whenPageIsNegative() throws Exception {
        mockMvc.perform(get("/notifications")
                .header(OrganisationHeaderFilter.ORGANISATION_ID_HEADER, ORGANISATION_ID)
                .param("page", "-1")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest());
    }

    @Test
    void findAll_shouldPassSortParamToService() throws Exception {
        when(queryService.findAll(1, "lastUpdated,asc", null, ORGANISATION_ID)).thenReturn(
            new AggregatedNotificationPageResponse(Collections.emptyList(), 1, 25, 0, 0, 0));

        mockMvc.perform(get("/notifications")
                .header(OrganisationHeaderFilter.ORGANISATION_ID_HEADER, ORGANISATION_ID)
                .param("sort", "lastUpdated,asc")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());

        verify(queryService).findAll(1, "lastUpdated,asc", null, ORGANISATION_ID);
    }

    @Test
    void findAll_shouldPassReferenceNumberParamToService() throws Exception {
        when(queryService.findAll(1, null, "GBN-AG-26-001", ORGANISATION_ID)).thenReturn(
            new AggregatedNotificationPageResponse(Collections.emptyList(), 1, 25, 0, 0, 0));

        mockMvc.perform(get("/notifications")
                .header(OrganisationHeaderFilter.ORGANISATION_ID_HEADER, ORGANISATION_ID)
                .param("referenceNumber", "GBN-AG-26-001")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());

        verify(queryService).findAll(1, null, "GBN-AG-26-001", ORGANISATION_ID);
    }

    @Test
    void findAll_shouldPassPageParamToService() throws Exception {
        when(queryService.findAll(2, null, null, ORGANISATION_ID)).thenReturn(
            new AggregatedNotificationPageResponse(Collections.emptyList(), 2, 25, 0, 120, 5));

        mockMvc.perform(get("/notifications")
                .header(OrganisationHeaderFilter.ORGANISATION_ID_HEADER, ORGANISATION_ID)
                .param("page", "2")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(2))
            .andExpect(jsonPath("$.totalElements").value(120))
            .andExpect(jsonPath("$.totalPages").value(5));

        verify(queryService).findAll(2, null, null, ORGANISATION_ID);
    }
}
