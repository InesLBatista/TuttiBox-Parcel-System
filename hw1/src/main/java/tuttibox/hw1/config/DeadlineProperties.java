package tuttibox.hw1.config;

import java.time.Duration;

import lombok.Data;

import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "tuttibox.deadlines")
public class DeadlineProperties {
    private Duration dropOff = Duration.ofHours(48);
    private Duration pickup = Duration.ofDays(7);
    private long checkIntervalMs = 60_000;
    private boolean schedulingEnabled = true;
}
