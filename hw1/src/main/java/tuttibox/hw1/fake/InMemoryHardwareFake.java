package tuttibox.hw1.fake;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import tuttibox.hw1.port.HardwareException;
import tuttibox.hw1.port.HardwarePort;

@Component 
public class InMemoryHardwareFake implements HardwarePort {
    private boolean injectFailure = false;
    private final Map<String,Boolean> doorStatus = new HashMap<>();

    public void setInjectFailure(boolean injectFailure) {
        this.injectFailure = injectFailure;
    }

    @Override 
    public void openCompartment(String stationId, String compartmentId) {
        if (injectFailure) {
            throw new HardwareException("hardware failure: failed to open compartment " + compartmentId);
        }
        //simulação de abertura da porta realizada com sucesso
        doorStatus.put(stationId + '-' + compartmentId, false);
    }

    @Override 
    public boolean isDoorClosed(String stationId, String compartmentId) {
        return doorStatus.getOrDefault(stationId + '-' + compartmentId, true);
    }
}
