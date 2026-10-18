package tuttibox.hw1.model;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Data;
import lombok.NoArgsConstructor;

@Entity 
@Table (name = "parcel_transitions")
@Data
@NoArgsConstructor
public class ParcelTransition {
    @Id 
    @GeneratedValue (strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private ParcelState fromState;

    @Enumerated(EnumType.STRING)
    private ParcelState toState;

    private String actor;
    private LocalDateTime timeStamp;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parcel_id")
    private Parcel parcel;

    public ParcelTransition(Parcel parcel, ParcelState fromState, ParcelState toState, String actor) {
        this.parcel=parcel;
        this.fromState=fromState;
        this.toState=toState;
        this.actor=actor;
        this.timeStamp = LocalDateTime.now();
    }
}
