package network.foodsharing.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The only place that touches the system clock. Everything else injects {@link Clock} (ArchUnit rule 5).
 */
@Configuration
public class ClockConfig {

    @Bean
    public MutableClock clock(AppProperties properties) {
        return new MutableClock(Clock.systemUTC(), properties.getClock().getFixed());
    }
}
