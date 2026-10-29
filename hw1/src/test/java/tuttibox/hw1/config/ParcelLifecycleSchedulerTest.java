package tuttibox.hw1.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tuttibox.hw1.service.ParcelService;

class ParcelLifecycleSchedulerTest {
    @Test
    @DisplayName("scheduler delega a aplicação de deadlines no serviço")
    void testApplyDeadlinesDelegatesToService() {
        ParcelService parcelService = mock(ParcelService.class);
        ParcelLifecycleScheduler scheduler = new ParcelLifecycleScheduler(parcelService);

        scheduler.applyDeadlines();

        verify(parcelService).applyDeadlines();
    }
}
