package tuttibox.hw1.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import tuttibox.hw1.dto.NearbyStationDto;
import tuttibox.hw1.service.StationSearchService;

@RestController
@RequestMapping("/api/stations")
@Tag(name = "Stations", description = "Address geocoding, nearby station ranking and available compartment capacity")
public class StationSearchController {
    private final StationSearchService stationSearchService;

    public StationSearchController(StationSearchService stationSearchService) {
        this.stationSearchService = stationSearchService;
    }

    @GetMapping("/search")
    @Operation(summary = "Find nearby stations", description = "Geocodes the address, ranks the seeded/database stations by distance, and returns free S/M/L capacity. A valid address without a match returns an empty array.")
    public ResponseEntity<List<NearbyStationDto>> searchByAddress(@RequestParam("address") String address) {
        return ResponseEntity.ok(stationSearchService.findNearestStations(address));
    }
}
