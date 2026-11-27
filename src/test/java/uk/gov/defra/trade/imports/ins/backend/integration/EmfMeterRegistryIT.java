package uk.gov.defra.trade.imports.ins.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.defra.trade.imports.ins.backend.testsupport.CapturedConsole.captureSystemOut;
import static uk.gov.defra.trade.imports.ins.backend.testsupport.EmfDocuments.isDocumentFor;
import static uk.gov.defra.trade.imports.ins.backend.testsupport.EmfDocuments.namespaceOf;

import com.fasterxml.jackson.databind.JsonNode;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import uk.gov.defra.trade.imports.ins.backend.metrics.EmfMeterRegistry;
import uk.gov.defra.trade.imports.ins.backend.notification.NotificationSqsListener;
import uk.gov.defra.trade.imports.ins.backend.testsupport.CapturedConsole;
import uk.gov.defra.trade.imports.ins.backend.testsupport.NotificationEventEnvelope;

@DirtiesContext
@TestPropertySource(
    properties = {
      "aws.emf.enabled=true",
      "aws.emf.environment=Local",
      "aws.emf.namespace=trade-imports-ins-backend",
      "aws.emf.step=1s"
    })
class EmfMeterRegistryIT extends IntegrationBase {

  private static final String AGGREGATE_ID = "Imports.Notification.GBN-AG.GBN-AG-26-900";

  @Autowired private MeterRegistry meterRegistry;
  @Autowired private NotificationSqsListener notificationSqsListener;

  @Test
  void meterRegistry_shouldBeTheEmfMeterRegistry_whenEmfIsEnabled() {
    assertThat(meterRegistry).isInstanceOf(EmfMeterRegistry.class);
  }

  @Test
  void allowlist_shouldLetThroughTheMetersANotificationListRecords() throws Exception {
    mockMvc.perform(listNotificationsFor(ORGANISATION_ID)).andExpect(status().isOk());

    for (String name :
        List.of(
            "external.call.duration",
            "external.call.failure",
            "controller.getAllNotifications.time",
            "service.findOrganisationPage.time")) {
      assertThat(meterRegistry.find(name).meter()).as(name).isNotNull();
    }
  }

  @Test
  void allowlist_shouldLetThroughTheMetersANotificationMessageRecords() {
    notificationSqsListener.receive(
        NotificationEventEnvelope.withData(
            AGGREGATE_ID, 1, ORGANISATION_ID, NotificationEventEnvelope.NOTIFICATION_EDITED, "{}"),
        AGGREGATE_ID,
        "1",
        null,
        UUID.randomUUID());

    for (String name :
        List.of(
            "listener.notification.time",
            "service.upsertNotification.time",
            "notification.ins.sqs.messages")) {
      assertThat(meterRegistry.find(name).meter()).as(name).isNotNull();
    }
  }

  @Test
  void allowlist_shouldLetThroughTheDependencyAvailabilityGauges() {
    assertThat(meterRegistry.find("external.dependency.available").tag("dependency", "mongodb").gauge())
        .isNotNull();
    assertThat(meterRegistry.find("external.dependency.available").tag("dependency", "sqs").gauge())
        .isNotNull();
  }

  @Test
  void emfMeterRegistry_shouldPublishARecordedCall_whenItsStepElapses() throws Exception {
    CapturedConsole console = captureSystemOut();
    try (console) {
      mockMvc.perform(listNotificationsFor(ORGANISATION_ID)).andExpect(status().isOk());

      await()
          .atMost(Duration.ofSeconds(10))
          .pollInterval(Duration.ofSeconds(1))
          .until(() -> emfDocumentFor("external.call.duration", console).isPresent());
    }

    JsonNode document = emfDocumentFor("external.call.duration", console).orElseThrow();
    assertThat(namespaceOf(document)).isEqualTo("trade-imports-ins-backend");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "cache.gets",
        "mongodb.driver.commands",
        "spring.data.repository.invocations",
        "http.server.requests.active",
        "notification.ins.sqs.errors"
      })
  void allowlist_shouldNotRegisterAMeter_whenItsNameIsNotListed(String name) {
    meterRegistry.counter(name).increment();

    assertThat(meterRegistry.find(name).meter()).isNull();
  }

  private static Optional<JsonNode> emfDocumentFor(String metricName, CapturedConsole console) {
    return console.jsonLines().stream()
        .filter(line -> isDocumentFor(line, metricName))
        .findFirst();
  }
}
