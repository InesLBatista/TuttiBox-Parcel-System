package tuttibox.hw1.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import tuttibox.hw1.model.Parcel;
import tuttibox.hw1.model.ParcelState;

@Repository 
public interface ParcelRepository extends JpaRepository<Parcel, Long> {
    Optional<Parcel> findByTrackingCode(String trackingCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Parcel> findByPickupCodeHash(String pickupCodeHash);

    List<Parcel> findAllByOrderByIdAsc();

    List<Parcel> findByStation_CodeOrderByIdAsc(String stationCode);

    List<Parcel> findByCurrentStateOrderByIdAsc(ParcelState state);

    List<Parcel> findByStation_CodeAndCurrentStateOrderByIdAsc(String stationCode, ParcelState state);

    List<Parcel> findByCurrentStateAndDropOffDeadlineBefore(ParcelState state, LocalDateTime now);

    List<Parcel> findByCurrentStateAndPickupDeadlineBefore(ParcelState state, LocalDateTime now);
}
