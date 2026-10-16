package tuttibox.hw1.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tuttibox.hw1.dto.ParcelRequestDto;
import tuttibox.hw1.model.Parcel;
import tuttibox.hw1.service.ParcelService;

@RestController 
@RequestMapping ("/api/parcels")
public class ParcelController {
    private final ParcelService parcelService;

    public ParcelController(ParcelService parcelService) {
        this.parcelService=parcelService;
    }

    @PostMapping 
    public ResponseEntity<Parcel> registerParcel(@RequestBody ParcelRequestDto request) {
        //tracking code único
        String trackingCode = "TRK-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Parcel parcel = new Parcel(trackingCode, request.getRecipientContact(), request.getCompartmentSize());
        //registo da ecomenda e reserva do seu compartimento
        Parcel createdParcel = parcelService.registerParcelForStation(parcel, request.getStationId(), request.getCompartmentSize());
        return ResponseEntity.status(HttpStatus.CREATED).body(createdParcel);
    }
}
