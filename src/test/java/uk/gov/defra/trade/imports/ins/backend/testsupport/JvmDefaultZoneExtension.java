package uk.gov.defra.trade.imports.ins.backend.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.util.TimeZone;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class JvmDefaultZoneExtension implements BeforeEachCallback, AfterEachCallback {

    public static final String WEST_OF_UTC = "America/New_York";
    public static final String EAST_OF_UTC = "Pacific/Auckland";

    private TimeZone originalZone;

    @Override
    public void beforeEach(ExtensionContext context) {
        originalZone = TimeZone.getDefault();
    }

    @Override
    public void afterEach(ExtensionContext context) {
        TimeZone.setDefault(originalZone);
    }

    public void setJvmDefaultZone(String zoneId) {
        TimeZone zone = TimeZone.getTimeZone(ZoneId.of(zoneId));
        long probeInstant = Instant.parse("2026-07-21T00:00:00Z").toEpochMilli();
        assertThat(zone.getOffset(probeInstant)).isNotZero();
        TimeZone.setDefault(zone);
    }
}
