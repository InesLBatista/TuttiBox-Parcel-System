package tuttibox.hw1.config;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(DeadlineProperties.class)
public class DeadlineConfiguration {
    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
