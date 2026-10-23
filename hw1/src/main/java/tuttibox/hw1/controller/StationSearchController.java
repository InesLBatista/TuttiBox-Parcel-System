package tuttibox.hw1.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tuttibox.hw1.dto.NearbyStationDto;
import tuttibox.hw1.service.StationSearchService;

@RestController
@RequestMapping("/api/stations")
public class StationSearchController {
    private final StationSearchService stationSearchService;

    public StationSearchController(StationSearchService stationSearchService) {
        this.stationSearchService = stationSearchService;
    }

    @GetMapping("/search")
    public ResponseEntity<List<NearbyStationDto>> searchByAddress(@RequestParam String address) {
        return ResponseEntity.ok(stationSearchService.findNearestStations(address));
    }
}
