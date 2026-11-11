package uk.gov.defra.trade.imports.ins.backend.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
@RequiredArgsConstructor
public class OrganisationHeaderFilter extends OncePerRequestFilter {

    public static final String ORGANISATION_ID_HEADER = "Trade-Imports-Organisation-Id";
    private static final URI TYPE_BAD_REQUEST =
        URI.create("https://api.cdp.defra.cloud/problems/bad-request");

    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        return !"/notifications".equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {

        String organisationId = request.getHeader(ORGANISATION_ID_HEADER);
        if (organisationId == null || organisationId.isBlank()) {
            rejectMissingHeader(response);
            return;
        }

        chain.doFilter(request, response);
    }

    private void rejectMissingHeader(HttpServletResponse response) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "Missing required header " + ORGANISATION_ID_HEADER);
        problem.setTitle("Bad Request");
        problem.setType(TYPE_BAD_REQUEST);
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getOutputStream().write(objectMapper.writeValueAsBytes(problem));
    }
}
