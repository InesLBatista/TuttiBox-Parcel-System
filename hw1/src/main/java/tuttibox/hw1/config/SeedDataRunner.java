package tuttibox.hw1.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    prefix = "tuttibox.seed",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class SeedDataRunner implements ApplicationRunner {
    private final SeedData seedData;

    public SeedDataRunner(SeedData seedData) {
        this.seedData = seedData;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedData.seed();
    }
}
