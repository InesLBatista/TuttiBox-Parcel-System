package tuttibox.hw1.service;

import java.util.List;

import org.springframework.stereotype.Service;

import tuttibox.hw1.model.Parcel;
import tuttibox.hw1.model.ParcelTransition;
import tuttibox.hw1.port.HardwarePort;
import tuttibox.hw1.repository.ParcelRepository;

@Service 
public class ParcelService {
    private final HardwarePort hardwarePort;
    private final ParcelRepository parcelRepository;

    public ParcelService(HardwarePort hardwarePort, ParcelRepository parcelRepository) {
        this.hardwarePort=hardwarePort;
        this.parcelRepository=parcelRepository;
    }

    public void dropOffParcel(Parcel parcel, String stationId, String compartmnentId, String courierActor) {
        //interage com hardware para abrir compartimento
        hardwarePort.openCompartment(stationId, compartmnentId);
        //atualizar estado da encomenda na máquina de estados
        parcel.markAsReadyForPickup(courierActor);
    }

    public void pickUpParcel(Parcel parcel, String stationId, String compartmentId, String recipientActor) {
        hardwarePort.openCompartment(stationId, compartmentId);
        parcel.pickUp(recipientActor);
    }

    public Parcel registerParcelForStation(Parcel parcel, String stationId, String compartmentSize) {
        //validação da disponibilidade do compartimento da estação
        //por agora, criar e retornar a ecomenda com estado CREATED gerado no model
        return parcel;
    }

    public Parcel processCourierDropOff(String trackingCode, String stationId, String compartmentId, String courierId) {
        //localização da ecomenda com tracking code na bd
        Parcel parcel = parcelRepository.findByTrackingCode(trackingCode)
            .orElseThrow(() -> new IllegalArgumentException("encomenda não foi encontrada: " + trackingCode));
        //abertura do hardware
        hardwarePort.openCompartment(stationId, compartmentId);
        //atualizar estado
        parcel.markAsReadyForPickup(courierId);
        //atualização bd
        return parcelRepository.save(parcel);
    }

    public Parcel processRecipientPickUp(String trackingCode, String stationId, String compartmentId, String recipientId) {
        Parcel parcel = parcelRepository.findByTrackingCode(trackingCode)
                .orElseThrow(() -> new IllegalArgumentException("encomenda não encontrada: " + trackingCode));

        hardwarePort.openCompartment(stationId, compartmentId);
        //transição na máquina de estados para PICKED_UP
        parcel.pickUp(recipientId);
        return parcelRepository.save(parcel);
    }

    public List<ParcelTransition> getParcelHistory(String trackingCode) {
        Parcel parcel = parcelRepository.findByTrackingCode(trackingCode)
                .orElseThrow(() -> new IllegalArgumentException("encomenda não foi encontrada: " + trackingCode));
        return parcel.getTransitions();
    }



    public Parcel processParcelExpiration(String trackingCode, String actor) {
        Parcel parcel = parcelRepository.findByTrackingCode(trackingCode)
                .orElseThrow(() -> new IllegalArgumentException("encomenda não encontrada: " + trackingCode));
        parcel.expire(actor);
        return parcelRepository.save(parcel);
    }

    public Parcel processParcelReturn(String trackingCode, String actor) {
        Parcel parcel = parcelRepository.findByTrackingCode(trackingCode)
                .orElseThrow(() -> new IllegalArgumentException("encomenda não encontrada: " + trackingCode));
        parcel.returnParcel(actor);
        return parcelRepository.save(parcel);
    }

    public Parcel processParcelCancellation(String trackingCode, String actor) {
        Parcel parcel = parcelRepository.findByTrackingCode(trackingCode)
                .orElseThrow(() -> new IllegalArgumentException("encomenda não encontrada: " + trackingCode));
        parcel.cancel(actor);
        return parcelRepository.save(parcel);
    }
}
