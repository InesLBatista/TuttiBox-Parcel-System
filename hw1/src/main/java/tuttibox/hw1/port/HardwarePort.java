package tuttibox.hw1.port;

public interface HardwarePort {
    void openCompartment(String stationId, String compartmentId) throws HardwareException;
    boolean isDoorClosed(String stationId, String compartmentId);
}