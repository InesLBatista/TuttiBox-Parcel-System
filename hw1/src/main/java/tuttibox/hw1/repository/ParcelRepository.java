package tuttibox.hw1.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import tuttibox.hw1.model.Parcel;

@Repository 
public interface ParcelRepository extends JpaRepository<Parcel, Long> {
    Optional<Parcel> findByTrackingCode(String trackingCode);
}
