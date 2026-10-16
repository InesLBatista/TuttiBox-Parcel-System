package tuttibox.hw1.service;

import org.springframework.stereotype.Service;

import tuttibox.hw1.model.Parcel;
import tuttibox.hw1.port.HardwarePort;

@Service 
public class ParcelService {
    private final HardwarePort hardwarePort;

    public ParcelService(HardwarePort hardwarePort) {
        this.hardwarePort=hardwarePort;
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
}
