package tuttibox.hw1.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tuttibox.hw1.exception.InvalidTransitionException;

public class ParcelTest {
    private Parcel parcel;

    @BeforeEach 
    void setup() {
        parcel = new Parcel ("TEST12345", "example@example.com", "L");
    }

    @Test 
    @DisplayName ("inicial state should be created with a history record")
    void testInitialState() {
        assertThat(parcel.getCurrentState()).isEqualTo(ParcelState.CREATED);
        assertThat(parcel.getTrackingCode()).isEqualTo("TEST12345");
        assertThat(parcel.getTransitions()).hasSize(1);
        assertThat(parcel.getTransitions().get(0).getToState()).isEqualTo(ParcelState.CREATED);
        assertThat(parcel.getTransitions().get(0).getActor()).isEqualTo("PARTNER");
    }

    @Test 
    @DisplayName ("transition from CREATED to READY_TO_PICKUP by courier should succed")
    void testSuccessfulDropOff() {
        parcel.markAsReadyForPickup("COURIER");

        assertThat(parcel.getCurrentState()).isEqualTo(ParcelState.READY_FOR_PICKUP);
        assertThat(parcel.getTransitions()).hasSize(2);
        
        ParcelTransition lastTransition = parcel.getTransitions().get(1);
        assertThat(lastTransition.getFromState()).isEqualTo(ParcelState.CREATED);
        assertThat(lastTransition.getToState()).isEqualTo(ParcelState.READY_FOR_PICKUP);
        assertThat(lastTransition.getActor()).isEqualTo("COURIER");
        assertThat(lastTransition.getTimeStamp()).isNotNull();
    }

    @Test 
    @DisplayName ("transition from CREATED to CANCELLED by partner should succed")
    void testSuccessfulCancellation() {
        parcel.cancel("PARTNER");

        assertThat(parcel.getCurrentState()).isEqualTo(ParcelState.CANCELLED);
        assertThat(parcel.getTransitions()).hasSize(2);
        assertThat(parcel.getTransitions().get(1).getToState()).isEqualTo(ParcelState.CANCELLED);
    }

    @Test
    @DisplayName("attempting to pick up an item in CREATED state should throw InvalidTransitionException")
    void testInvalidPickupFromCreated() {
        assertThatThrownBy(() -> parcel.pickUp("RECIPIENT"))
                .isInstanceOf(InvalidTransitionException.class);
        
        //garantir aqui que de facto o estado não mudou, ou seja, foi impossível a transação de CREATED para READY_FOR_PICKUP
        assertThat(parcel.getCurrentState()).isEqualTo(ParcelState.CREATED);
    }

    @Test
    @DisplayName("complete lifecycle")
    void testCompleteSuccessfulLifecycle() {
        parcel.markAsReadyForPickup("COURIER");
        assertThat(parcel.getCurrentState()).isEqualTo(ParcelState.READY_FOR_PICKUP);

        parcel.pickUp("RECIPIENT");
        assertThat(parcel.getCurrentState()).isEqualTo(ParcelState.PICKED_UP);
        assertThat(parcel.getTransitions()).hasSize(3);
    }

    @Test
    @DisplayName("complete expiration lifecycle")
    void testCompleteExpirationLifecycle() {
        parcel.markAsReadyForPickup("COURIER");
        
        parcel.expire("SYSTEM");
        assertThat(parcel.getCurrentState()).isEqualTo(ParcelState.EXPIRED);

        parcel.returnParcel("OPERATOR");
        assertThat(parcel.getCurrentState()).isEqualTo(ParcelState.RETURNED);
        assertThat(parcel.getTransitions()).hasSize(4);
    }
}