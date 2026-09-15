package com.datn.foodshare.controller;

import com.datn.foodshare.domain.response.FoodPostResponse;
import com.datn.foodshare.service.matching.MatchingPipelineService;
import com.datn.foodshare.util.annotation.ApiMessage;
import com.datn.foodshare.util.error.PermissionException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/matching")
@RequiredArgsConstructor
public class MatchingController {

    private final MatchingPipelineService matchingPipelineService;

    @GetMapping("/recommendations")
    @Secured({"ROLE_RECIPIENT", "ROLE_ORGANIZATION"})
    @ApiMessage("Lấy danh sách gợi ý phù hợp thành công")
    public ResponseEntity<List<FoodPostResponse>> getRecommendations(
            @RequestParam(name = "size", defaultValue = "6") int size) throws PermissionException {
        return ResponseEntity.ok(matchingPipelineService.recommendForCurrentUser(size));
    }
}
