package tuttibox.hw1.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import tuttibox.hw1.TestcontainersConfiguration;
import tuttibox.hw1.model.Compartment;
import tuttibox.hw1.repository.CompartmentRepository;
import tuttibox.hw1.repository.ParcelRepository;
import tuttibox.hw1.repository.StationRepository;

@SpringBootTest
@Transactional
@Import(TestcontainersConfiguration.class)
class SeedDataIntegrationTest {
    @Autowired
    private SeedData seedData;
    @Autowired
    private ParcelRepository parcelRepository;
    @Autowired
    private CompartmentRepository compartmentRepository;
    @Autowired
    private StationRepository stationRepository;

    @BeforeEach
    void setUp() {
        parcelRepository.deleteAll();
        compartmentRepository.deleteAll();
        stationRepository.deleteAll();
    }

    @Test
    @DisplayName("seed cria estações e compartimentos e é idempotente")
    void testSeedIsIdempotent() {
        seedData.seed();

        assertThat(stationRepository.count()).isEqualTo(3);
        assertThat(compartmentRepository.count()).isEqualTo(18);
        assertThat(compartmentRepository.findByStation_CodeOrderByCodeAsc("ST-AVEIRO-CENTRO"))
            .extracting(Compartment::getCode)
            .containsExactly("C-L1", "C-L2", "C-M1", "C-M2", "C-S1", "C-S2");

        seedData.seed();

        assertThat(stationRepository.count()).isEqualTo(3);
        assertThat(compartmentRepository.count()).isEqualTo(18);
    }
}
