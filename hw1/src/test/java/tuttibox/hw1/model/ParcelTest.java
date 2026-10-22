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
    @DisplayName ("estado inicial deve ser CREATED com um registo de histórico")
    void testInitialState() {
        assertThat(parcel.getCurrentState()).isEqualTo(ParcelState.CREATED);
        assertThat(parcel.getTrackingCode()).isEqualTo("TEST12345");
        assertThat(parcel.getTransitions()).hasSize(1);
        assertThat(parcel.getTransitions().get(0).getToState()).isEqualTo(ParcelState.CREATED);
        assertThat(parcel.getTransitions().get(0).getActor()).isEqualTo("PARTNER");
    }

    @Test 
    @DisplayName ("transição de CREATED para READY_FOR_PICKUP pelo courier deve ser bem-sucedida")
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
    @DisplayName ("transição de CREATED para CANCELLED pelo partner deve ser bem-sucedida")
    void testSuccessfulCancellation() {
        parcel.cancel("PARTNER");

        assertThat(parcel.getCurrentState()).isEqualTo(ParcelState.CANCELLED);
        assertThat(parcel.getTransitions()).hasSize(2);
        assertThat(parcel.getTransitions().get(1).getToState()).isEqualTo(ParcelState.CANCELLED);
    }

    @Test
    @DisplayName("tentar fazer pickup no estado CREATED deve lançar InvalidTransitionException")
    void testInvalidPickupFromCreated() {
        assertThatThrownBy(() -> parcel.pickUp("RECIPIENT"))
                .isInstanceOf(InvalidTransitionException.class);
        
        //garantir aqui que de facto o estado não mudou, ou seja, foi impossível a transação de CREATED para READY_FOR_PICKUP
        assertThat(parcel.getCurrentState()).isEqualTo(ParcelState.CREATED);
    }

    @Test
    @DisplayName("ciclo de vida completo")
    void testCompleteSuccessfulLifecycle() {
        parcel.markAsReadyForPickup("COURIER");
        assertThat(parcel.getCurrentState()).isEqualTo(ParcelState.READY_FOR_PICKUP);

        parcel.pickUp("RECIPIENT");
        assertThat(parcel.getCurrentState()).isEqualTo(ParcelState.PICKED_UP);
        assertThat(parcel.getTransitions()).hasSize(3);
    }

    @Test
    @DisplayName("ciclo de vida completo de expiração")
    void testCompleteExpirationLifecycle() {
        parcel.markAsReadyForPickup("COURIER");
        
        parcel.expire("SYSTEM");
        assertThat(parcel.getCurrentState()).isEqualTo(ParcelState.EXPIRED);

        parcel.returnParcel("OPERATOR");
        assertThat(parcel.getCurrentState()).isEqualTo(ParcelState.RETURNED);
        assertThat(parcel.getTransitions()).hasSize(4);
    }

    @Test
    @DisplayName("não é possível marcar como pronta para recolha uma encomenda que já está pronta")
    void testInvalidMarkAsReadyForPickupFromReady() {
        parcel.markAsReadyForPickup("COURIER");

        assertThatThrownBy(() -> parcel.markAsReadyForPickup("COURIER_2"))
                .isInstanceOf(InvalidTransitionException.class)
                .hasMessageContaining("READY_FOR_PICKUP");

        assertThat(parcel.getCurrentState()).isEqualTo(ParcelState.READY_FOR_PICKUP);
        assertThat(parcel.getTransitions()).hasSize(2);
    }

    @Test
    @DisplayName("não é possível cancelar uma encomenda depois de sair do estado CREATED")
    void testInvalidCancellationAfterDropOff() {
        parcel.markAsReadyForPickup("COURIER");

        assertThatThrownBy(() -> parcel.cancel("PARTNER"))
                .isInstanceOf(InvalidTransitionException.class)
                .hasMessageContaining("CREATED");

        assertThat(parcel.getCurrentState()).isEqualTo(ParcelState.READY_FOR_PICKUP);
        assertThat(parcel.getTransitions()).hasSize(2);
    }

    @Test
    @DisplayName("não é possível expirar uma encomenda que não está pronta para recolha")
    void testInvalidExpirationFromCreated() {
        assertThatThrownBy(() -> parcel.expire("SYSTEM"))
                .isInstanceOf(InvalidTransitionException.class)
                .hasMessageContaining("prontas para recolha");

        assertThat(parcel.getCurrentState()).isEqualTo(ParcelState.CREATED);
        assertThat(parcel.getTransitions()).hasSize(1);
    }

    @Test
    @DisplayName("não é possível devolver uma encomenda que não expirou")
    void testInvalidReturnFromReadyForPickup() {
        parcel.markAsReadyForPickup("COURIER");

        assertThatThrownBy(() -> parcel.returnParcel("OPERATOR"))
                .isInstanceOf(InvalidTransitionException.class)
                .hasMessageContaining("expiraram");

        assertThat(parcel.getCurrentState()).isEqualTo(ParcelState.READY_FOR_PICKUP);
        assertThat(parcel.getTransitions()).hasSize(2);
    }
}