package uk.gov.defra.trade.imports.ins.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.defra.trade.imports.ins.backend.testsupport.CapturedConsole.captureSystemOut;
import static uk.gov.defra.trade.imports.ins.backend.testsupport.ExternalCallMeters.MONGODB_FIND;
import static uk.gov.defra.trade.imports.ins.backend.testsupport.ExternalCallMeters.MONGODB_INSERT;
import static uk.gov.defra.trade.imports.ins.backend.testsupport.ExternalCallMeters.MONGODB_UPDATE;
import static uk.gov.defra.trade.imports.ins.backend.testsupport.ExternalCallMeters.SQS_RECEIVE_MESSAGE;
import static uk.gov.defra.trade.imports.ins.backend.testsupport.ExternalCallMeters.availability;
import static uk.gov.defra.trade.imports.ins.backend.testsupport.ExternalCallMeters.durationSampleCount;
import static uk.gov.defra.trade.imports.ins.backend.testsupport.ExternalCallMeters.failureTotal;

import com.fasterxml.jackson.databind.JsonNode;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import uk.gov.defra.trade.imports.ins.backend.metrics.Dependency;
import uk.gov.defra.trade.imports.ins.backend.notification.NotificationSqsListener;
import uk.gov.defra.trade.imports.ins.backend.testsupport.CapturedConsole;
import uk.gov.defra.trade.imports.ins.backend.testsupport.NotificationEventEnvelope;

class ExternalCallRecorderIT extends IntegrationBase {

  private static final String SCRATCH_COLLECTION = "external_call_recorder_it";
  private static final String AGGREGATE_ID = "Imports.Notification.GBN-AG.GBN-AG-26-901";

  @Autowired private MeterRegistry meterRegistry;
  @Autowired private NotificationSqsListener notificationSqsListener;

  @AfterEach
  void dropScratchCollection() {
    mongoTemplate.dropCollection(SCRATCH_COLLECTION);
  }

  @Test
  void listNotifications_shouldRecordAMongodbFind() throws Exception {
    long before = durationSampleCount(meterRegistry, MONGODB_FIND);

    mockMvc.perform(listNotificationsFor(ORGANISATION_ID)).andExpect(status().isOk());

    assertThat(durationSampleCount(meterRegistry, MONGODB_FIND)).isGreaterThan(before);
  }

  @Test
  void notificationMessage_shouldRecordAMongodbUpdate() {
    long before = durationSampleCount(meterRegistry, MONGODB_UPDATE);

    notificationSqsListener.receive(
        NotificationEventEnvelope.withData(
            AGGREGATE_ID, 1, ORGANISATION_ID, NotificationEventEnvelope.NOTIFICATION_EDITED, "{}"),
        AGGREGATE_ID,
        "1");

    assertThat(durationSampleCount(meterRegistry, MONGODB_UPDATE)).isGreaterThan(before);
  }

  @Test
  void queueListener_shouldRecordEachReceiveAsASuccessOfSqs() {
    long before = durationSampleCount(meterRegistry, SQS_RECEIVE_MESSAGE);

    await()
        .atMost(Duration.ofSeconds(30))
        .until(() -> durationSampleCount(meterRegistry, SQS_RECEIVE_MESSAGE) > before);

    assertThat(failureTotal(meterRegistry, SQS_RECEIVE_MESSAGE)).isZero();
    assertThat(availability(meterRegistry, Dependency.SQS)).isEqualTo(1);
  }

  @Test
  void find_shouldRecordAFailure_whenTheCommandFails() {
    double before = failureTotal(meterRegistry, MONGODB_FIND);

    assertThatExceptionOfType(DataAccessException.class).isThrownBy(this::runFailingFind);

    assertThat(failureTotal(meterRegistry, MONGODB_FIND)).isEqualTo(before + 1);
  }

  @Test
  void insert_shouldRecordASuccess_whenTheKeyIsADuplicate() {
    Document document = new Document("_id", new ObjectId());
    mongoTemplate.insert(document, SCRATCH_COLLECTION);
    long callsBefore = durationSampleCount(meterRegistry, MONGODB_INSERT);
    double failuresBefore = failureTotal(meterRegistry, MONGODB_INSERT);

    assertThatExceptionOfType(DuplicateKeyException.class)
        .isThrownBy(() -> mongoTemplate.insert(document, SCRATCH_COLLECTION));

    assertThat(durationSampleCount(meterRegistry, MONGODB_INSERT)).isEqualTo(callsBefore + 1);
    assertThat(failureTotal(meterRegistry, MONGODB_INSERT)).isEqualTo(failuresBefore);
  }

  @Test
  void listNotifications_shouldWriteTheCallLineWithTheTraceIdAndANestedEventObject()
      throws Exception {
    CapturedConsole console = captureSystemOut();
    try (console) {
      mockMvc
          .perform(
              listNotificationsFor(ORGANISATION_ID).header("x-cdp-request-id", "trace-from-the-caller"))
          .andExpect(status().isOk());
    }

    JsonNode find = consoleLineWithAction("mongodb/find", console);

    assertThat(find.path("trace.id").asText()).isEqualTo("trace-from-the-caller");
    assertThat(find.path("event").path("category").asText()).isEqualTo("external-call");
    assertThat(find.path("event").path("outcome").asText()).isEqualTo("success");
    assertThat(find.path("event").path("duration").isIntegralNumber()).isTrue();
  }

  @Test
  void find_shouldWriteAFailedCallLineWithNoStackTrace_whenTheCommandFails() {
    CapturedConsole console = captureSystemOut();
    try (console) {
      assertThatExceptionOfType(DataAccessException.class).isThrownBy(this::runFailingFind);
    }

    JsonNode failedFind = consoleLineWithAction("mongodb/find", console);

    assertThat(failedFind.path("event").path("outcome").asText()).isEqualTo("failure");
    assertThat(failedFind.path("error.type").asText())
        .isEqualTo("com.mongodb.MongoCommandException");
    assertThat(failedFind.path("error.message").asText()).contains("$notAnOperator");
    assertThat(failedFind.has("error.stack_trace")).isFalse();
  }

  private void runFailingFind() {
    mongoTemplate.executeCommand(
        "{find: '" + SCRATCH_COLLECTION + "', filter: {$notAnOperator: 1}}");
  }

  private static JsonNode consoleLineWithAction(String action, CapturedConsole console) {
    return console.jsonLines().stream()
        .filter(line -> action.equals(line.path("event").path("action").asText()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No console line has event.action " + action));
  }
}
