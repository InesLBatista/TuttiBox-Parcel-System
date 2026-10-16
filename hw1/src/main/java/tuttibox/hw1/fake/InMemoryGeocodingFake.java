package tuttibox.hw1.fake;

import java.util.HashMap;

import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import tuttibox.hw1.port.GeocodingPort;
import tuttibox.hw1.model.*;;

@Component 
@Primary 
public class InMemoryGeocodingFake implements GeocodingPort {
    private final Map<String, Coordinates> addressDatabase = new HashMap<>();
    private boolean simulateUnavailable = false;

    public InMemoryGeocodingFake() {
        //dados para testes/demostração
        addressDatabase.put("Anadia", new Coordinates(40.6305, -8.6571));
        addressDatabase.put("Aradas", new Coordinates(40.6418, -8.6444));
    }

    public void setSimulateUnavailable(boolean simulateUnavailable) {
        this.simulateUnavailable=simulateUnavailable;
    }

    public void addAddress(String address, Coordinates coordinates) {
        addressDatabase.put(address, coordinates);
    }

    @Override 
    public Optional<Coordinates> getCoordinates(String address) {
        if (simulateUnavailable) {
            //simulação da inidsponibilidade de serviço externo de geolocalização
            throw new RuntimeException("geocoding service is unavailable");
        }

        return Optional.ofNullable(addressDatabase.get(address));
    }
}
