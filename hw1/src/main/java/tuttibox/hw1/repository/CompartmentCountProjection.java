package tuttibox.hw1.repository;

import tuttibox.hw1.model.CompartmentSize;

public interface CompartmentCountProjection {
    String getStationCode();
    CompartmentSize getSize();
    long getTotal();
}
