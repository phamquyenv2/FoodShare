package com.datn.foodshare.controller;

import com.datn.foodshare.domain.response.LocationSuggestionResponse;
import com.datn.foodshare.service.LocationService;
import com.datn.foodshare.util.annotation.ApiMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    @GetMapping("/autocomplete")
    @ApiMessage("Gợi ý địa chỉ thành công")
    public ResponseEntity<List<LocationSuggestionResponse>> autocomplete(@RequestParam("text") String text) {
        return ResponseEntity.ok(locationService.autocomplete(text));
    }

    @GetMapping("/reverse")
    @ApiMessage("Xác định địa chỉ từ vị trí thành công")
    public ResponseEntity<LocationSuggestionResponse> reverse(
            @RequestParam("latitude") BigDecimal latitude,
            @RequestParam("longitude") BigDecimal longitude) {
        return ResponseEntity.ok(locationService.reverse(latitude, longitude));
    }
}
