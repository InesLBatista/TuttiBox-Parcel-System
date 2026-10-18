package tuttibox.hw1.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tuttibox.hw1.dto.DropOffRequestDto;
import tuttibox.hw1.dto.ParcelRequestDto;
import tuttibox.hw1.dto.ParcelResponseDto;
import tuttibox.hw1.dto.PickUpRequestDto;
import tuttibox.hw1.model.Parcel;
import tuttibox.hw1.model.ParcelTransition;
import tuttibox.hw1.service.ParcelService;

@RestController 
@RequestMapping("/api/parcels")
public class ParcelController {
    private final ParcelService parcelService;

    public ParcelController(ParcelService parcelService) {
        this.parcelService=parcelService;
    }

    //registo da encomenda
    @PostMapping
    public ResponseEntity<ParcelResponseDto> registerParcel(@Valid @RequestBody ParcelRequestDto request) {
        ParcelResponseDto response = parcelService.registerParcel(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{trackingCode}/dropoff")
    public ResponseEntity<Parcel> courierDropOff(@PathVariable String trackingCode, @Valid @RequestBody DropOffRequestDto request) {
        Parcel updated = parcelService.processCourierDropOff(
            trackingCode, request.getStationId(), request.getCompartmentId(), request.getCourierId());
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{trackingCode}/pickup")
    public ResponseEntity<Parcel> recipientPickUp(@PathVariable String trackingCode, @Valid @RequestBody PickUpRequestDto request) {
        Parcel updated = parcelService.processRecipientPickUp(
            trackingCode, request.getStationId(), request.getCompartmentId(), request.getRecipientId());
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/{trackingCode}/history")
    public ResponseEntity<List<ParcelTransition>> getParcelHistory(@PathVariable String trackingCode) {
        return ResponseEntity.ok(parcelService.getParcelHistory(trackingCode));
    }

    @PostMapping("/{trackingCode}/cancel")
    public ResponseEntity<Parcel> cancelParcel(@PathVariable String trackingCode, @RequestParam String actor) {
        return ResponseEntity.ok(parcelService.processParcelCancellation(trackingCode, actor));
    }

    @PostMapping("/{trackingCode}/expire")
    public ResponseEntity<Parcel> expireParcel(@PathVariable String trackingCode, @RequestParam String actor) {
        return ResponseEntity.ok(parcelService.processParcelExpiration(trackingCode, actor));
    }

    @PostMapping("/{trackingCode}/return")
    public ResponseEntity<Parcel> returnParcel(@PathVariable String trackingCode, @RequestParam String actor) {
        return ResponseEntity.ok(parcelService.processParcelReturn(trackingCode, actor));
    }
}
