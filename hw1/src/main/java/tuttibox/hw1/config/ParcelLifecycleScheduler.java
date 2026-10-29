package tuttibox.hw1.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import tuttibox.hw1.service.ParcelService;

@Component
@ConditionalOnProperty(
    prefix = "tuttibox.deadlines",
    name = "scheduling-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ParcelLifecycleScheduler {
    private final ParcelService parcelService;

    public ParcelLifecycleScheduler(ParcelService parcelService) {
        this.parcelService = parcelService;
    }

    @Scheduled(fixedDelayString = "${tuttibox.deadlines.check-interval-ms:60000}")
    public void applyDeadlines() {
        parcelService.applyDeadlines();
    }
}
