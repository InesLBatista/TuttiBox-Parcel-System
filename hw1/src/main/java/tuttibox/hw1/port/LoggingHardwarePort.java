package tuttibox.hw1.port;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingHardwarePort implements HardwarePort {
    private static final Logger log = LoggerFactory.getLogger(LoggingHardwarePort.class);
    private final HardwarePort delegate;

    public LoggingHardwarePort(HardwarePort delegate) {
        this.delegate = delegate;
    }

    @Override
    public void openCompartment(String stationId, String compartmentId) {
        log.info("hardware chamada: openCompartment estação={}, compartimento={}",
            stationId, compartmentId);
        try {
            delegate.openCompartment(stationId, compartmentId);
            log.info("hardware resultado: openCompartment estação={}, compartimento={}, sucesso=true",
                stationId, compartmentId);
        } catch (RuntimeException ex) {
            log.warn("hardware resultado: openCompartment estação={}, compartimento={}, sucesso=false, erro={}",
                stationId, compartmentId, ex.getMessage());
            throw ex;
        }
    }

    @Override
    public void confirmDoorClosed(String stationId, String compartmentId) {
        log.info("hardware chamada: confirmDoorClosed estação={}, compartimento={}",
            stationId, compartmentId);
        try {
            delegate.confirmDoorClosed(stationId, compartmentId);
            log.info("hardware resultado: confirmDoorClosed estação={}, compartimento={}, sucesso=true",
                stationId, compartmentId);
        } catch (RuntimeException ex) {
            log.warn("hardware resultado: confirmDoorClosed estação={}, compartimento={}, sucesso=false, erro={}",
                stationId, compartmentId, ex.getMessage());
            throw ex;
        }
    }

    @Override
    public boolean isDoorClosed(String stationId, String compartmentId) {
        log.info("hardware chamada: isDoorClosed estação={}, compartimento={}",
            stationId, compartmentId);
        try {
            boolean closed = delegate.isDoorClosed(stationId, compartmentId);
            log.info("hardware resultado: isDoorClosed estação={}, compartimento={}, fechada={}",
                stationId, compartmentId, closed);
            return closed;
        } catch (RuntimeException ex) {
            log.warn("hardware resultado: isDoorClosed estação={}, compartimento={}, sucesso=false, erro={}",
                stationId, compartmentId, ex.getMessage());
            throw ex;
        }
    }
}
