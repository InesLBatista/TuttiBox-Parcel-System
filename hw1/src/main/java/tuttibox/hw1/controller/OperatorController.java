package tuttibox.hw1.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import tuttibox.hw1.dto.CompartmentResponseDto;
import tuttibox.hw1.dto.ParcelResponseDto;
import tuttibox.hw1.model.ParcelState;
import tuttibox.hw1.service.ParcelService;

@RestController
@RequestMapping("/api/operator")
@Tag(name = "Operator", description = "Parcel supervision, expired parcel removal and compartment maintenance")
public class OperatorController {
    private final ParcelService parcelService;

    public OperatorController(ParcelService parcelService) {
        this.parcelService = parcelService;
    }

    @GetMapping("/parcels")
    @Operation(summary = "List and filter parcels", description = "Optionally filters parcels by station ID and lifecycle state.")
    public ResponseEntity<List<ParcelResponseDto>> listParcels(
            @RequestParam(required = false) String stationId,
            @RequestParam(required = false) ParcelState state) {
        return ResponseEntity.ok(parcelService.listParcels(stationId, state));
    }

    @GetMapping("/parcels/expired")
    @Operation(summary = "List expired parcels", description = "Lists expired parcels, optionally limited to one station.")
    public ResponseEntity<List<ParcelResponseDto>> listExpiredParcels(
            @RequestParam(required = false) String stationId) {
        return ResponseEntity.ok(parcelService.listExpiredParcels(stationId));
    }

    @PostMapping("/parcels/{trackingCode}/remove")
    @Operation(summary = "Remove an expired parcel", description = "Marks the parcel RETURNED and releases its occupied compartment.")
    public ResponseEntity<ParcelResponseDto> removeExpiredParcel(
            @PathVariable("trackingCode") String trackingCode,
            @RequestParam("actor") String actor) {
        return ResponseEntity.ok(parcelService.removeExpiredParcel(trackingCode, actor));
    }

    @GetMapping("/stations/{stationId}/compartments")
    @Operation(summary = "List station compartments", description = "Returns compartment codes, sizes and statuses for the requested station.")
    public ResponseEntity<List<CompartmentResponseDto>> listCompartments(
            @PathVariable("stationId") String stationId) {
        return ResponseEntity.ok(parcelService.listCompartments(stationId));
    }

    @PostMapping("/stations/{stationId}/compartments/{compartmentId}/out-of-service")
    @Operation(summary = "Take a compartment out of service", description = "Only a FREE compartment can be disabled. Returns 409 when it is reserved or occupied.")
    public ResponseEntity<CompartmentResponseDto> putCompartmentOutOfService(
            @PathVariable("stationId") String stationId,
            @PathVariable("compartmentId") String compartmentId,
            @RequestParam("actor") String actor) {
        return ResponseEntity.ok(
            parcelService.putCompartmentOutOfService(stationId, compartmentId, actor));
    }

    @PostMapping("/stations/{stationId}/compartments/{compartmentId}/in-service")
    @Operation(summary = "Restore a compartment to service", description = "Returns an OUT_OF_SERVICE compartment to FREE. Returns 409 when it is not currently out of service.")
    public ResponseEntity<CompartmentResponseDto> putCompartmentInService(
            @PathVariable("stationId") String stationId,
            @PathVariable("compartmentId") String compartmentId,
            @RequestParam("actor") String actor) {
        return ResponseEntity.ok(
            parcelService.putCompartmentInService(stationId, compartmentId, actor));
    }
}
