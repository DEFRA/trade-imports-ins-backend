package uk.gov.defra.trade.imports.ins.backend.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NotificationSqsConfigTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static final String VALID_URL =
        "https://sqs.eu-west-2.amazonaws.com/123456789012/notifications.fifo";

    private static NotificationSqsConfig config(String queueUrl, int waitTimeSeconds, int maxMessages) {
        return new NotificationSqsConfig(queueUrl, waitTimeSeconds, maxMessages);
    }

    @Test
    void notificationSqsConfig_shouldHaveNoViolations_whenAllFieldsValid() {
        assertThat(VALIDATOR.validate(config(VALID_URL, 10, 10))).isEmpty();
    }

    @Test
    void notificationSqsConfig_shouldFlagQueueUrl_whenBlank() {
        Set<ConstraintViolation<NotificationSqsConfig>> violations =
            VALIDATOR.validate(config("", 10, 10));

        assertThat(violations)
            .extracting(v -> v.getPropertyPath().toString())
            .containsExactly("queueUrl");
    }

    @Test
    void notificationSqsConfig_shouldFlagWaitTimeSeconds_whenNegative() {
        Set<ConstraintViolation<NotificationSqsConfig>> violations =
            VALIDATOR.validate(config(VALID_URL, -1, 10));

        assertThat(violations)
            .extracting(v -> v.getPropertyPath().toString())
            .containsExactly("waitTimeSeconds");
    }

    @Test
    void notificationSqsConfig_shouldFlagWaitTimeSeconds_whenAboveMax() {
        Set<ConstraintViolation<NotificationSqsConfig>> violations =
            VALIDATOR.validate(config(VALID_URL, 21, 10));

        assertThat(violations)
            .extracting(v -> v.getPropertyPath().toString())
            .containsExactly("waitTimeSeconds");
    }

    @Test
    void notificationSqsConfig_shouldFlagMaxMessages_whenZero() {
        Set<ConstraintViolation<NotificationSqsConfig>> violations =
            VALIDATOR.validate(config(VALID_URL, 10, 0));

        assertThat(violations)
            .extracting(v -> v.getPropertyPath().toString())
            .containsExactly("maxMessages");
    }

    @Test
    void notificationSqsConfig_shouldFlagMaxMessages_whenAboveMax() {
        Set<ConstraintViolation<NotificationSqsConfig>> violations =
            VALIDATOR.validate(config(VALID_URL, 10, 11));

        assertThat(violations)
            .extracting(v -> v.getPropertyPath().toString())
            .containsExactly("maxMessages");
    }
}
