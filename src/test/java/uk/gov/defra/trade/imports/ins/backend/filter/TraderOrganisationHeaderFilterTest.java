package uk.gov.defra.trade.imports.ins.backend.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static uk.gov.defra.trade.imports.ins.backend.filter.TraderOrganisationHeaderFilter.ORGANISATION_ID_HEADER;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class TraderOrganisationHeaderFilterTest {

    private static final String TYPE_BAD_REQUEST =
        "https://api.cdp.defra.cloud/problems/bad-request";

    @Mock
    private FilterChain chain;

    private TraderOrganisationHeaderFilter filter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        filter = new TraderOrganisationHeaderFilter(objectMapper);
    }

    @Test
    void shouldNotFilter_shouldSkip_whenMethodIsNotGet() {
        MockHttpServletRequest request = getNotificationsRequest();
        request.setMethod("POST");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_shouldSkip_whenPathIsNotNotifications() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        request.setServletPath("/health");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_shouldApply_whenGetNotifications() {
        assertThat(filter.shouldNotFilter(getNotificationsRequest())).isFalse();
    }

    @Test
    void doFilter_shouldContinueChain_whenOrganisationHeaderIsPresent() throws Exception {
        MockHttpServletRequest request = getNotificationsRequest();
        request.addHeader(ORGANISATION_ID_HEADER, "5900001");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(same(request), same(response));
    }

    @Test
    void doFilter_shouldReturnBadRequestProblem_whenOrganisationHeaderIsMissing() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(getNotificationsRequest(), response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertBadRequestProblem(response);
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("detail").asText()).contains(ORGANISATION_ID_HEADER);
    }

    @Test
    void doFilter_shouldReturnBadRequestProblem_whenOrganisationHeaderIsBlank() throws Exception {
        MockHttpServletRequest request = getNotificationsRequest();
        request.addHeader(ORGANISATION_ID_HEADER, "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertBadRequestProblem(response);
    }

    private static void assertBadRequestProblem(MockHttpServletResponse response) throws Exception {
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(response.getContentType()).isEqualTo("application/problem+json");
        JsonNode body = new ObjectMapper().readTree(response.getContentAsString());
        assertThat(body.get("status").asInt()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(body.get("type").asText()).isEqualTo(TYPE_BAD_REQUEST);
        assertThat(body.get("title").asText()).isEqualTo("Bad Request");
        assertThat(body.get("detail").asText()).contains(ORGANISATION_ID_HEADER);
    }

    private static MockHttpServletRequest getNotificationsRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/notifications");
        request.setServletPath("/notifications");
        return request;
    }
}