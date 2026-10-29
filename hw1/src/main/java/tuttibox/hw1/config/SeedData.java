package tuttibox.hw1.config;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import tuttibox.hw1.model.Compartment;
import tuttibox.hw1.model.CompartmentSize;
import tuttibox.hw1.model.Station;
import tuttibox.hw1.repository.CompartmentRepository;
import tuttibox.hw1.repository.StationRepository;

@Component
public class SeedData {
    private static final Logger log = LoggerFactory.getLogger(SeedData.class);

    private static final List<StationSeed> STATIONS = List.of(
        new StationSeed("ST-AVEIRO-CENTRO", "Aveiro - Centro", 40.6405, -8.6538,
            List.of(CompartmentSize.S, CompartmentSize.S,
                CompartmentSize.M, CompartmentSize.M,
                CompartmentSize.L, CompartmentSize.L)),
        new StationSeed("ST-AVEIRO-FORUM", "Aveiro - Fórum", 40.6390, -8.6520,
            List.of(CompartmentSize.S, CompartmentSize.S,
                CompartmentSize.M, CompartmentSize.M,
                CompartmentSize.L, CompartmentSize.L)),
        new StationSeed("ST-AVEIRO-ESTACAO", "Aveiro - Estação", 40.6435, -8.6505,
            List.of(CompartmentSize.S, CompartmentSize.S,
                CompartmentSize.M, CompartmentSize.M,
                CompartmentSize.L, CompartmentSize.L))
    );

    private final StationRepository stationRepository;
    private final CompartmentRepository compartmentRepository;

    public SeedData(StationRepository stationRepository, CompartmentRepository compartmentRepository) {
        this.stationRepository = stationRepository;
        this.compartmentRepository = compartmentRepository;
    }

    @Transactional
    public void seed() {
        int createdStations = 0;
        int createdCompartments = 0;

        for (StationSeed seed : STATIONS) {
            Station station = stationRepository.findByCode(seed.code()).orElse(null);
            if (station == null) {
                station = stationRepository.save(new Station(
                    seed.code(), seed.name(), seed.latitude(), seed.longitude()));
                createdStations++;
            }

            Map<CompartmentSize, Integer> counters = new EnumMap<>(CompartmentSize.class);
            for (CompartmentSize size : seed.compartmentSizes()) {
                int index = counters.merge(size, 1, Integer::sum);
                String compartmentCode = "C-" + size + index;
                if (compartmentRepository.findByStationCodeAndCode(seed.code(), compartmentCode).isEmpty()) {
                    compartmentRepository.save(new Compartment(compartmentCode, station, size));
                    createdCompartments++;
                }
            }
        }

        log.info("seed de dados concluído: estações criadas={}, compartimentos criados={}",
            createdStations, createdCompartments);
    }

    private record StationSeed(
            String code,
            String name,
            double latitude,
            double longitude,
            List<CompartmentSize> compartmentSizes) {
    }
}
