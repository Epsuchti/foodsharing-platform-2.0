package network.foodsharing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;

/** The app and the test JVMs run in UTC, so zone-less seed timestamps mean the same instant on every machine. */
class JvmTimeZoneTest {

    @Test
    void appAndTestsRunInUtc() {
        assertThat(FoodsharingApplication.JVM_TIME_ZONE.getID()).isEqualTo("UTC");
        // surefire/failsafe argLine: the tests see the same default zone as the jar
        assertThat(TimeZone.getDefault().getID()).isEqualTo("UTC");
        // a seed value such as '2026-11-04T17:00:00' is 18:00 in Zurich
        Instant seeded = LocalDateTime.parse("2026-11-04T17:00:00").atZone(ZoneId.systemDefault()).toInstant();
        assertThat(seeded.atZone(ZoneId.of("Europe/Zurich")).getHour()).isEqualTo(18);
    }
}
