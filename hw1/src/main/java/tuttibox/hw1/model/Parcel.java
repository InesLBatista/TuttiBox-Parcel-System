package tuttibox.hw1.model;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import tuttibox.hw1.exception.InvalidTransitionException;

@Entity 
@Table (name = "parcels")
@Data 
@NoArgsConstructor 
public class Parcel {
    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String trackingCode;
    private String recepientContact;
    private String compartmentSize;

    @Enumerated (EnumType.STRING)
    private ParcelState currentState;

    @OneToMany (cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ParcelTransition> history = new ArrayList<>();

    public Parcel(String trackingCode, String recepientContact, String compartmentSize) {
        this.trackingCode=trackingCode;
        this.recepientContact=recepientContact;
        this.compartmentSize=compartmentSize;
        this.currentState=ParcelState.CREATED;

        this.history.add(new ParcelTransition(null, ParcelState.CREATED, "PARTNER"));
    }

    public void markAsReadyForPickup(String actor) {
        if (this.currentState != ParcelState.CREATED) {
            throw new InvalidTransitionException("cannot transition from " + this.currentState + " to READY_FOR_PICKUP");
        }
        transitionTo(ParcelState.READY_FOR_PICKUP, actor);
    }

    public void cancel (String actor) {
        if (this.currentState != ParcelState.CREATED) {
            throw new InvalidTransitionException("parcel can only be cancelled when in CREATED state");
        }
        transitionTo(ParcelState.CANCELLED, actor);
    }

    public void pickUp(String actor) {
        if (this.currentState != ParcelState.READY_FOR_PICKUP) {
            throw new InvalidTransitionException("parcel is not ready for pickup");
        }
        transitionTo(ParcelState.PICKED_UP, actor);
    }

    public void expire(String actor) {
        if (this.currentState != ParcelState.READY_FOR_PICKUP) {
            throw new InvalidTransitionException("only items ready for pickup can expire");
        }
        transitionTo(ParcelState.EXPIRED, actor);
    }

    public void returnParcel(String actor) {
        if (this.currentState != ParcelState.EXPIRED) {
            throw new InvalidTransitionException("only expired parcels can be returned");
        }
        transitionTo(ParcelState.RETURNED, actor);
    }

    private void transitionTo(ParcelState newState, String actor) {
        ParcelTransition transition = new ParcelTransition(this.currentState, newState, actor);
        this.history.add(transition);
        this.currentState=newState;
    }
}