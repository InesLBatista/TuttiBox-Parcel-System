package tuttibox.hw1.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tuttibox.hw1.dto.CompartmentResponseDto;
import tuttibox.hw1.dto.ParcelResponseDto;
import tuttibox.hw1.model.ParcelState;
import tuttibox.hw1.service.ParcelService;

@RestController
@RequestMapping("/api/operator")
public class OperatorController {
    private final ParcelService parcelService;

    public OperatorController(ParcelService parcelService) {
        this.parcelService = parcelService;
    }

    @GetMapping("/parcels")
    public ResponseEntity<List<ParcelResponseDto>> listParcels(
            @RequestParam(required = false) String stationId,
            @RequestParam(required = false) ParcelState state) {
        return ResponseEntity.ok(parcelService.listParcels(stationId, state));
    }

    @GetMapping("/parcels/expired")
    public ResponseEntity<List<ParcelResponseDto>> listExpiredParcels(
            @RequestParam(required = false) String stationId) {
        return ResponseEntity.ok(parcelService.listExpiredParcels(stationId));
    }

    @PostMapping("/parcels/{trackingCode}/remove")
    public ResponseEntity<ParcelResponseDto> removeExpiredParcel(
            @PathVariable String trackingCode,
            @RequestParam String actor) {
        return ResponseEntity.ok(parcelService.removeExpiredParcel(trackingCode, actor));
    }

    @GetMapping("/stations/{stationId}/compartments")
    public ResponseEntity<List<CompartmentResponseDto>> listCompartments(
            @PathVariable String stationId) {
        return ResponseEntity.ok(parcelService.listCompartments(stationId));
    }

    @PostMapping("/stations/{stationId}/compartments/{compartmentId}/out-of-service")
    public ResponseEntity<CompartmentResponseDto> putCompartmentOutOfService(
            @PathVariable String stationId,
            @PathVariable String compartmentId,
            @RequestParam String actor) {
        return ResponseEntity.ok(
            parcelService.putCompartmentOutOfService(stationId, compartmentId, actor));
    }

    @PostMapping("/stations/{stationId}/compartments/{compartmentId}/in-service")
    public ResponseEntity<CompartmentResponseDto> putCompartmentInService(
            @PathVariable String stationId,
            @PathVariable String compartmentId,
            @RequestParam String actor) {
        return ResponseEntity.ok(
            parcelService.putCompartmentInService(stationId, compartmentId, actor));
    }
}
