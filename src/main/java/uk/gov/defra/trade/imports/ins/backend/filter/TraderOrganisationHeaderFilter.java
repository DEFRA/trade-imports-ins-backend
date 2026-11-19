package uk.gov.defra.trade.imports.ins.backend.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import uk.gov.defra.trade.imports.ins.backend.support.ProblemDetailsFactory;

/**
 * Requires {@link #ORGANISATION_ID_HEADER} on {@code GET /notifications}; missing or blank
 * values are refused with HTTP 400 and an {@code application/problem+json} body.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
@Slf4j
@RequiredArgsConstructor
public class TraderOrganisationHeaderFilter extends OncePerRequestFilter {

    public static final String ORGANISATION_ID_HEADER = "Trade-Imports-Organisation-Id";
    private static final URI TYPE_BAD_REQUEST =
        URI.create("https://api.cdp.defra.cloud/problems/bad-request");

    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        return !"/notifications".equals(servletPath(request));
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {

        String organisationId = request.getHeader(ORGANISATION_ID_HEADER);
        if (organisationId == null || organisationId.isBlank()) {
            log.warn(
                "Refused {} {}: header {} missing or blank",
                request.getMethod(),
                request.getRequestURI(),
                ORGANISATION_ID_HEADER);
            rejectMissingHeader(response);
            return;
        }

        chain.doFilter(request, response);
    }

    static String servletPath(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        if (servletPath != null && !servletPath.isEmpty()) {
            return servletPath;
        }
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri != null ? uri : "";
    }

    private void rejectMissingHeader(HttpServletResponse response) throws IOException {
        String detail = "Missing required header " + ORGANISATION_ID_HEADER;
        ProblemDetail problem =
            ProblemDetailsFactory.problemDetail(
                HttpStatus.BAD_REQUEST, TYPE_BAD_REQUEST, "Bad Request", detail);
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getOutputStream().write(objectMapper.writeValueAsBytes(problem));
    }
}