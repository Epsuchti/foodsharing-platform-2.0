package network.foodsharing;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FoodsharingApplication {

    /**
     * The JVM runs in UTC on every machine. Liquibase reads zone-less seed timestamps ({@code valueDate:
     * '2026-11-04T17:00:00'}) in the JVM's default zone, so a jar started on a developer machine (Windows here:
     * Europe/Kiev) seeded every event two hours earlier than the same jar in Docker (UTC), and the screenshots
     * differed by machine. The app itself formats with explicit zones (Europe/Zurich, store/region zones).
     */
    static final TimeZone JVM_TIME_ZONE = TimeZone.getTimeZone("UTC");

    public static void main(String[] args) {
        TimeZone.setDefault(JVM_TIME_ZONE);
        SpringApplication.run(FoodsharingApplication.class, args);
    }
}
