package tuttibox.hw1.fake;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import tuttibox.hw1.port.HardwareException;
import tuttibox.hw1.port.HardwarePort;

@Component 
public class InMemoryHardwareFake implements HardwarePort {
    private boolean injectFailure = false;
    private boolean injectCloseFailure = false;
    private boolean injectDoorNotClosed = false;
    private final Map<String,Boolean> doorStatus = new HashMap<>();

    public void setInjectFailure(boolean injectFailure) {
        this.injectFailure = injectFailure;
    }

    public void setInjectCloseFailure(boolean injectCloseFailure) {
        this.injectCloseFailure = injectCloseFailure;
    }

    public void setInjectDoorNotClosed(boolean injectDoorNotClosed) {
        this.injectDoorNotClosed = injectDoorNotClosed;
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
    public void confirmDoorClosed(String stationId, String compartmentId) {
        if (injectCloseFailure) {
            throw new HardwareException("hardware failure: failed to confirm closing of compartment " + compartmentId);
        }
        if (!injectDoorNotClosed) {
            doorStatus.put(stationId + '-' + compartmentId, true);
        }
    }

    @Override 
    public boolean isDoorClosed(String stationId, String compartmentId) {
        return doorStatus.getOrDefault(stationId + '-' + compartmentId, true);
    }
}
