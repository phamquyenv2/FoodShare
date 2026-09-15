package com.datn.foodshare.controller;

import com.datn.foodshare.domain.response.FoodPostResponse;
import com.datn.foodshare.service.matching.MatchingPipelineService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchingControllerTest {

    @Test
    void getRecommendations_delegatesToUserSpecificPipeline() throws Exception {
        MatchingPipelineService pipeline = mock(MatchingPipelineService.class);
        MatchingController controller = new MatchingController(pipeline);
        FoodPostResponse recommendation = FoodPostResponse.builder()
                .id(10L)
                .matchScore(87.0)
                .build();
        when(pipeline.recommendForCurrentUser(6)).thenReturn(List.of(recommendation));

        var response = controller.getRecommendations(6);

        assertEquals(List.of(recommendation), response.getBody());
        verify(pipeline).recommendForCurrentUser(6);
    }
}
