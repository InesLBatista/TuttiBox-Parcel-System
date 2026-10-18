package tuttibox.hw1.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tuttibox.hw1.dto.DropOffRequestDto;
import tuttibox.hw1.dto.ParcelRequestDto;
import tuttibox.hw1.dto.PickUpRequestDto;
import tuttibox.hw1.model.Parcel;
import tuttibox.hw1.model.ParcelTransition;
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

    @PostMapping("/{trackingCode}/dropoff")
    public ResponseEntity<Parcel> courierDropOff(@PathVariable String trackingCode, @RequestBody DropOffRequestDto request) {
        Parcel updatedParcel = parcelService.processCourierDropOff(trackingCode,
                request.getStationId(),
                request.getCompartmentId(),
                request.getCourierId()
        );
        
        return ResponseEntity.ok(updatedParcel);
    }

    @PostMapping("/{trackingCode}/pickup")
    public ResponseEntity<Parcel> recipientPickUp(@PathVariable String trackingCode, @RequestBody PickUpRequestDto request) {
        Parcel updatedParcel = parcelService.processRecipientPickUp(
                trackingCode,
                request.getStationId(),
                request.getCompartmentId(),
                request.getRecipientId()
        );

        return ResponseEntity.ok(updatedParcel);
    }

    @GetMapping("/{trackingCode}/history")
    public ResponseEntity<List<ParcelTransition>> getParcelHistory(@PathVariable String trackingCode) {
        List<ParcelTransition> history = parcelService.getParcelHistory(trackingCode);
        return ResponseEntity.ok(history);
    }
}
