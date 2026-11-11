package uk.gov.defra.trade.imports.ins.backend.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static uk.gov.defra.trade.imports.ins.backend.filter.OrganisationHeaderFilter.ORGANISATION_ID_HEADER;

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
class OrganisationHeaderFilterTest {

    @Mock
    private FilterChain chain;

    private OrganisationHeaderFilter filter;

    @BeforeEach
    void setup() {
        filter = new OrganisationHeaderFilter(new ObjectMapper());
    }

    @Test
    void shouldNotFilter_whenMethodIsNotGet() {
        MockHttpServletRequest request = getNotificationsRequest();
        request.setMethod("POST");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_whenGetRequestIsNotNotifications() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        request.setServletPath("/health");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldFilter_whenGetRequestTargetsNotifications() {
        assertThat(filter.shouldNotFilter(getNotificationsRequest())).isFalse();
    }

    @Test
    void doFilter_continuesWhenOrganisationHeaderIsPresent() throws Exception {
        MockHttpServletRequest request = getNotificationsRequest();
        request.addHeader(ORGANISATION_ID_HEADER, "5900001");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(same(request), same(response));
    }

    @Test
    void doFilter_returnsBadRequestProblem_whenOrganisationHeaderIsMissing() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(getNotificationsRequest(), response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(response.getContentType()).isEqualTo("application/problem+json");
        assertThat(response.getContentAsString()).contains(ORGANISATION_ID_HEADER);
    }

    @Test
    void doFilter_returnsBadRequestProblem_whenOrganisationHeaderIsBlank() throws Exception {
        MockHttpServletRequest request = getNotificationsRequest();
        request.addHeader(ORGANISATION_ID_HEADER, "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    private static MockHttpServletRequest getNotificationsRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/notifications");
        request.setServletPath("/notifications");
        return request;
    }
}
