package tuttibox.hw1.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tuttibox.hw1.exception.InvalidTransitionException;

@Entity 
@Table (name = "parcels")
@Data 
@NoArgsConstructor 
public class Parcel {
    private static final Logger log = LoggerFactory.getLogger(Parcel.class);

    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String trackingCode;

    private String recepientContact;
    private String compartmentSize;
    private String partnerId;

    private LocalDateTime registeredAt;
    private LocalDateTime dropOffDeadline;
    private LocalDateTime pickupDeadline;

    //apenas o hash é guardado; o código em claro só existe na notificação ao destinatário
    @JsonIgnore
    @Column(name = "pickup_code_hash", length = 64, unique = true)
    private String pickupCodeHash;

    @Enumerated(EnumType.STRING)
    private ParcelState currentState;

    //station onde encomenda foi registada
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id")
    @JsonIgnore
    private Station station;

    //compartimento reservado
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "compartment_id")
    @JsonIgnore
    private Compartment assignedCompartment;

    @OneToMany(mappedBy = "parcel", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<ParcelTransition> transitions = new ArrayList<>();

    public Parcel(String trackingCode, String recepientContact, String compartmentSize) {
        this.trackingCode=trackingCode;
        this.recepientContact=recepientContact;
        this.compartmentSize=compartmentSize;

        transitionTo(ParcelState.CREATED, "PARTNER");
    }



    public void markAsReadyForPickup(String actor) {
        markAsReadyForPickup(actor, null);
    }

    public void markAsReadyForPickup(String actor, LocalDateTime pickupDeadline) {
        if (this.currentState != ParcelState.CREATED) {
            throw new InvalidTransitionException(
                "não é possível a transição de " + this.currentState + " para READY_FOR_PICKUP");
        }
        transitionTo(ParcelState.READY_FOR_PICKUP, actor);
        this.pickupDeadline = pickupDeadline;
    }

    public void issuePickupCode(String pickupCodeHash) {
        if (this.currentState != ParcelState.READY_FOR_PICKUP) {
            throw new InvalidTransitionException(
                "o código de recolha só pode ser emitido depois do drop-off");
        }
        if (pickupCodeHash == null || pickupCodeHash.isBlank()) {
            throw new IllegalArgumentException("o hash do código de recolha é obrigatório");
        }
        this.pickupCodeHash = pickupCodeHash;
    }

    public void cancel (String actor) {
        if (this.currentState != ParcelState.CREATED) {
            throw new InvalidTransitionException("parcel só pode ser cancelado quando se encontra no estado CREATED");
        }
        transitionTo(ParcelState.CANCELLED, actor);
    }

    public void pickUp(String actor) {
        if (this.currentState != ParcelState.READY_FOR_PICKUP) {
            throw new InvalidTransitionException("parcel ainda não está pronto para pick up");
        }
        transitionTo(ParcelState.PICKED_UP, actor);
        this.pickupCodeHash = null;
    }

    public void expire(String actor) {
        if (this.currentState != ParcelState.READY_FOR_PICKUP) {
            throw new InvalidTransitionException("apenas encomendas prontas para recolha podem expirar");
        }
        transitionTo(ParcelState.EXPIRED, actor);
    }

    public void returnParcel(String actor) {
        if (this.currentState != ParcelState.EXPIRED) {
            throw new InvalidTransitionException("só podem ser devolvidas parcels que já expiraram");
        }
        transitionTo(ParcelState.RETURNED, actor);
    }

    public boolean isDropOffDeadlineExceeded(LocalDateTime now) {
        return dropOffDeadline != null && now.isAfter(dropOffDeadline);
    }

    public boolean isPickupDeadlineExceeded(LocalDateTime now) {
        return pickupDeadline != null && now.isAfter(pickupDeadline);
    }

    private void transitionTo(ParcelState newState, String actor) {
        ParcelState previousState = this.currentState;
        this.transitions.add(new ParcelTransition(this, previousState, newState, actor));
        this.currentState = newState;
        log.info("transição de estado da encomenda: trackingCode={}, de={}, para={}, ator={}",
            trackingCode, previousState, newState, actor);
    }
}