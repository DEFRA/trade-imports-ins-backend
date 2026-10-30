package uk.gov.defra.trade.imports.ins.backend.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.defra.trade.imports.ins.backend.testsupport.JvmDefaultZoneExtension.EAST_OF_UTC;
import static uk.gov.defra.trade.imports.ins.backend.testsupport.JvmDefaultZoneExtension.WEST_OF_UTC;

import java.time.LocalDate;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.gov.defra.trade.imports.ins.backend.configuration.LocalDateStringConverters.LocalDateToStringConverter;
import uk.gov.defra.trade.imports.ins.backend.configuration.LocalDateStringConverters.StringToLocalDateConverter;
import uk.gov.defra.trade.imports.ins.backend.testsupport.JvmDefaultZoneExtension;

class LocalDateStringConvertersTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 21);
    private static final String ISO_DATE = "2026-07-21";

    @RegisterExtension
    final JvmDefaultZoneExtension jvmDefaultZone = new JvmDefaultZoneExtension();

    @ParameterizedTest
    @ValueSource(strings = {WEST_OF_UTC, EAST_OF_UTC})
    void convertToString_shouldReturnTheIsoDateString_whateverTheJvmDefaultZone(String zoneId) {
        jvmDefaultZone.setJvmDefaultZone(zoneId);

        assertThat(new LocalDateToStringConverter().convert(DATE)).isEqualTo(ISO_DATE);
    }

    @ParameterizedTest
    @ValueSource(strings = {WEST_OF_UTC, EAST_OF_UTC})
    void convertToLocalDate_shouldParseTheIsoDateStringToTheSameCalendarDate_whateverTheJvmDefaultZone(String zoneId) {
        jvmDefaultZone.setJvmDefaultZone(zoneId);

        assertThat(new StringToLocalDateConverter().convert(ISO_DATE)).isEqualTo(DATE);
    }
}
