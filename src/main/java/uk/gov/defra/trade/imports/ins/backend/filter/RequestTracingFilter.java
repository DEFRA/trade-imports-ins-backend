package uk.gov.defra.trade.imports.ins.backend.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static uk.gov.defra.trade.imports.ins.backend.logging.TraceContext.MDC_TRACE_ID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTracingFilter implements Filter {

    private static final String MDC_HTTP_METHOD = "http.request.method";
    private static final String MDC_URL_FULL = "url.full";

    @Value("${cdp.tracing.header-name}")
    private String header;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!(request instanceof HttpServletRequest httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        try {
            String traceId = httpRequest.getHeader(header);
            if (traceId != null && !traceId.isBlank()) {
                MDC.put(MDC_TRACE_ID, traceId);
            }

            MDC.put(MDC_HTTP_METHOD, httpRequest.getMethod());
            MDC.put(MDC_URL_FULL, httpRequest.getRequestURL().toString());

            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
