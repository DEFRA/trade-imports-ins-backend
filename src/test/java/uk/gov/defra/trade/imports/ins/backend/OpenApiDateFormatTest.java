package uk.gov.defra.trade.imports.ins.backend;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.media.Schema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import uk.gov.defra.trade.imports.ins.backend.notification.AggregatedNotification;
import uk.gov.defra.trade.imports.ins.backend.notification.AggregatedNotificationPageResponse;

class OpenApiDateFormatTest {

    @ParameterizedTest
    @CsvSource({
        "arrivalDate, date",
        "lastUpdated, date-time"
    })
    void schema_shouldDescribeEachDateFieldWithTheFormatOfItsType(
        String property, String expectedFormat) {
        List<Class<?>> apiTypes = List.of(
            AggregatedNotification.class,
            AggregatedNotificationPageResponse.class);
        List<String> formats = new ArrayList<>();
        for (Class<?> type : apiTypes) {
            for (Schema<?> schema : ModelConverters.getInstance().readAll(type).values()) {
                Map<String, Schema> properties = schema.getProperties();
                if (properties != null && properties.containsKey(property)) {
                    formats.add(properties.get(property).getFormat());
                }
            }
        }

        assertThat(formats)
            .as("format of every '%s' property in the API schemas", property)
            .isNotEmpty()
            .containsOnly(expectedFormat);
    }
}
