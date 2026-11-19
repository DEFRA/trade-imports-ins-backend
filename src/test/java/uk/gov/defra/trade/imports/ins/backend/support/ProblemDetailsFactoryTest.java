package uk.gov.defra.trade.imports.ins.backend.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

class ProblemDetailsFactoryTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void problemDetail_shouldOmitTraceId_whenMdcEmpty() {
        ProblemDetail problem =
            ProblemDetailsFactory.problemDetail(
                HttpStatus.BAD_REQUEST,
                URI.create("https://example.test/problems/bad-request"),
                "Bad Request",
                "detail");

        assertThat(problem.getProperties()).isNull();
    }

    @Test
    void problemDetail_shouldIncludeTraceId_whenMdcHasTraceId() {
        MDC.put("trace.id", "trace-abc");

        ProblemDetail problem =
            ProblemDetailsFactory.problemDetail(
                HttpStatus.BAD_REQUEST,
                URI.create("https://example.test/problems/bad-request"),
                "Bad Request",
                "detail");

        assertThat(problem.getProperties()).containsEntry("traceId", "trace-abc");
    }
}