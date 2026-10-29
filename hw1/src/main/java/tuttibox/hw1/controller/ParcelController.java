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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import tuttibox.hw1.dto.DropOffRequestDto;
import tuttibox.hw1.dto.ParcelRequestDto;
import tuttibox.hw1.dto.ParcelResponseDto;
import tuttibox.hw1.dto.PickUpRequestDto;
import tuttibox.hw1.model.Parcel;
import tuttibox.hw1.model.ParcelState;
import tuttibox.hw1.model.ParcelTransition;
import tuttibox.hw1.service.ParcelService;

@RestController 
@RequestMapping("/api/parcels")
@Tag(name = "Parcels", description = "Partner registration, courier deposit, recipient pickup and parcel tracking")
public class ParcelController {
    private final ParcelService parcelService;

    public ParcelController(ParcelService parcelService) {
        this.parcelService=parcelService;
    }

    //registo da encomenda
    @PostMapping
    @Operation(summary = "Register a parcel", description = "Reserves the smallest compatible free compartment and returns a tracking code. Returns 201, 400 for invalid input, 404 for an unknown station or 409 when no compatible compartment is free.")
    public ResponseEntity<ParcelResponseDto> registerParcel(@Valid @RequestBody ParcelRequestDto request) {
        ParcelResponseDto response = parcelService.registerParcel(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{trackingCode}/dropoff")
    @Operation(summary = "Open the assigned compartment for courier drop-off", description = "Opens the assigned locker. The parcel stays CREATED until door closure is confirmed. Returns 400 for an invalid assignment/state, 404 for an unknown tracking code or 503 for hardware failure.")
    public ResponseEntity<Parcel> courierDropOff(@PathVariable("trackingCode") String trackingCode, @Valid @RequestBody DropOffRequestDto request) {
        Parcel updated = parcelService.processCourierDropOff(
            trackingCode, request.getStationId(), request.getCompartmentId(), request.getCourierId());
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{trackingCode}/dropoff/confirm")
    @Operation(summary = "Confirm courier drop-off", description = "Confirms the assigned door is closed, marks the parcel READY_FOR_PICKUP and issues a pickup code through the notification port. Returns 400, 404 or 503 when the operation cannot complete.")
    public ResponseEntity<Parcel> courierConfirmDropOff(@PathVariable("trackingCode") String trackingCode, @Valid @RequestBody DropOffRequestDto request) {
        Parcel updated = parcelService.confirmCourierDropOff(
            trackingCode, request.getStationId(), request.getCompartmentId(), request.getCourierId());
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/pickup")
    @Operation(summary = "Pick up a parcel using its code", description = "Validates a one-time pickup code and opens its assigned compartment. Unknown, invalid, expired and reused codes share a generic 400 response; hardware failures return 503.")
    public ResponseEntity<Parcel> recipientPickUp(@Valid @RequestBody PickUpRequestDto request) {
        return ResponseEntity.ok(parcelService.processRecipientPickUp(request.getPickupCode()));
    }

    @GetMapping("/{trackingCode}/history")
    @Operation(summary = "Get parcel transition history", description = "Returns the ordered state transitions, actors and timestamps. Returns 404 when the tracking code is unknown.")
    public ResponseEntity<List<ParcelTransition>> getParcelHistory(@PathVariable("trackingCode") String trackingCode) {
        return ResponseEntity.ok(parcelService.getParcelHistory(trackingCode));
    }

    @GetMapping("/{trackingCode}")
    @Operation(summary = "Get parcel details", description = "Returns parcel details and current lifecycle data without exposing the pickup-code hash. Returns 404 when the tracking code is unknown.")
    public ResponseEntity<Parcel> getParcel(@PathVariable("trackingCode") String trackingCode) {
        return ResponseEntity.ok(parcelService.findByTrackingCode(trackingCode));
    }

    @GetMapping("/{trackingCode}/status")
    @Operation(summary = "Get current parcel state", description = "Returns the parcel state enum value. Returns 404 when the tracking code is unknown.")
    public ResponseEntity<ParcelState> getParcelStatus(@PathVariable("trackingCode") String trackingCode) {
        return ResponseEntity.ok(parcelService.findByTrackingCode(trackingCode).getCurrentState());
    }

    @PostMapping("/{trackingCode}/cancel")
    @Operation(summary = "Cancel a parcel", description = "Cancels a CREATED parcel and releases its reservation. Returns 400 for an invalid transition or missing actor and 404 for an unknown tracking code.")
    public ResponseEntity<Parcel> cancelParcel(@PathVariable("trackingCode") String trackingCode, @RequestParam("actor") String actor) {
        return ResponseEntity.ok(parcelService.processParcelCancellation(trackingCode, actor));
    }

    @PostMapping("/{trackingCode}/expire")
    @Operation(summary = "Expire a ready parcel", description = "Applies the pickup-expiration transition to a READY_FOR_PICKUP parcel. The compartment remains occupied until operator removal.")
    public ResponseEntity<Parcel> expireParcel(@PathVariable("trackingCode") String trackingCode, @RequestParam("actor") String actor) {
        return ResponseEntity.ok(parcelService.processParcelExpiration(trackingCode, actor));
    }

    @PostMapping("/{trackingCode}/return")
    @Operation(summary = "Return an expired parcel", description = "Marks an EXPIRED parcel as RETURNED and releases its compartment. Returns 400 for an invalid transition and 404 for an unknown tracking code.")
    public ResponseEntity<Parcel> returnParcel(@PathVariable("trackingCode") String trackingCode, @RequestParam("actor") String actor) {
        return ResponseEntity.ok(parcelService.processParcelReturn(trackingCode, actor));
    }
}
