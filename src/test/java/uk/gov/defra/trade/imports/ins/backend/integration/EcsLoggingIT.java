package uk.gov.defra.trade.imports.ins.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.defra.trade.imports.ins.backend.testsupport.CapturedConsole.captureSystemOut;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.junit.jupiter.api.Test;
import uk.gov.defra.trade.imports.ins.backend.testsupport.CapturedConsole;

class EcsLoggingIT extends IntegrationBase {

  @Test
  void requestLogging_shouldProduceEcsJsonFormat() throws Exception {
    try (CapturedConsole console = captureSystemOut()) {
      mockMvc.perform(get("/actuator/info")
              .header("x-cdp-request-id", "test-trace-123"))
          .andExpect(status().isNotFound());

      List<JsonNode> requestLines = requestLogLines(console);

      assertThat(requestLines).isNotEmpty();
      JsonNode logJson = requestLines.getFirst();

      assertThat(logJson.has("@timestamp")).isTrue();
      assertThat(logJson.has("log.level")).isTrue();
      assertThat(logJson.has("message")).isTrue();

      assertThat(logJson.has("trace.id")).isTrue();
      assertThat(logJson.get("trace.id").asText()).isEqualTo("test-trace-123");

      assertThat(logJson.has("http.request.method")).isTrue();
      assertThat(logJson.get("http.request.method").asText()).isEqualTo("GET");

      assertThat(logJson.has("url.full")).isTrue();
      assertThat(logJson.get("url.full").asText()).contains("/actuator/info");

      assertThat(logJson.has("service.name")).isTrue();
      assertThat(logJson.has("service.version")).isTrue();
    }
  }

  @Test
  void healthEndpoint_shouldBeFilteredFromLogs() throws Exception {
    try (CapturedConsole console = captureSystemOut()) {
      mockMvc.perform(get("/health"))
          .andExpect(status().isOk());

      assertThat(console.text()).doesNotContain("/health");
    }
  }

  @Test
  void missingTraceIdHeader_shouldLeaveTraceIdEmpty() throws Exception {
    try (CapturedConsole console = captureSystemOut()) {
      mockMvc.perform(get("/actuator/info"))
          .andExpect(status().isNotFound());

      assertThat(requestLogLines(console))
          .isNotEmpty()
          .allSatisfy(logJson -> assertThat(logJson.has("trace.id")).isFalse());
    }
  }

  private static List<JsonNode> requestLogLines(CapturedConsole console) {
    return console.jsonLines().stream().filter(line -> line.has("url.full")).toList();
  }
}
