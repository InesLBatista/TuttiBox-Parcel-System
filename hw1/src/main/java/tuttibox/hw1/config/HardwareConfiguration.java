package tuttibox.hw1.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import tuttibox.hw1.fake.InMemoryHardwareFake;
import tuttibox.hw1.port.HardwarePort;
import tuttibox.hw1.port.LoggingHardwarePort;

@Configuration
public class HardwareConfiguration {

    @Bean
    @Primary
    public HardwarePort loggingHardwarePort(InMemoryHardwareFake hardwareFake) {
        return new LoggingHardwarePort(hardwareFake);
    }
}
