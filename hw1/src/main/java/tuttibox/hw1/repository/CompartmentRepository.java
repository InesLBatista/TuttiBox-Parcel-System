package tuttibox.hw1.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import tuttibox.hw1.model.Compartment;
import tuttibox.hw1.model.CompartmentSize;
import tuttibox.hw1.model.CompartmentStatus;

@Repository
public interface CompartmentRepository extends JpaRepository<Compartment, Long> {
    //PESSIMISTIC_WRITE para evitar double-booking concorrente
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT c FROM Compartment c
        WHERE c.station.code = :stationCode
          AND c.status = :status
          AND c.size IN :sizes
        ORDER BY
          CASE c.size
            WHEN 'S' THEN 0
            WHEN 'M' THEN 1
            ELSE 2
          END ASC,
          c.code ASC
        """)
    List<Compartment> findAvailableByStationAndSizes(
        @Param("stationCode") String                      stationCode,
        @Param("status")      CompartmentStatus           status,
        @Param("sizes")       Collection<CompartmentSize> sizes
    );

    @Query("""
        SELECT c.station.code AS stationCode, c.size AS size, COUNT(c) AS total
        FROM Compartment c
        WHERE c.station.code IN :stationCodes
          AND c.status = :status
        GROUP BY c.station.code, c.size
        """)
    List<CompartmentCountProjection> countByStationCodesAndStatusGroupedBySize(
        @Param("stationCodes") Collection<String>  stationCodes,
        @Param("status")       CompartmentStatus   status
    );

    Optional<Compartment> findByStationCodeAndCode(String stationCode, String compartmentCode);
}
