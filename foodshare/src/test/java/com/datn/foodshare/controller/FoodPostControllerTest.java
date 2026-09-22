package com.datn.foodshare.controller;

import com.datn.foodshare.domain.request.CreateFoodPostRequest;
import com.datn.foodshare.domain.request.FoodPostFilterRequest;
import com.datn.foodshare.domain.request.UpdateFoodPostRequest;
import com.datn.foodshare.domain.response.FoodPostResponse;
import com.datn.foodshare.service.FoodPostService;
import com.datn.foodshare.util.constant.PostStatus;
import com.datn.foodshare.util.error.PermissionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FoodPostControllerTest {

    @Mock
    private FoodPostService foodPostService;

    private FoodPostController controller;

    @BeforeEach
    void setUp() {
        controller = new FoodPostController(foodPostService);
    }

    private FoodPostResponse mockPostResponse(Long id) {
        return FoodPostResponse.builder().id(id).name("Food Post " + id).build();
    }

    @Test
    void getPublicList_returns200() {
        Pageable pageable = PageRequest.of(0, 20);
        FoodPostFilterRequest filter = new FoodPostFilterRequest();
        Page<FoodPostResponse> page = new PageImpl<>(List.of(mockPostResponse(1L)));
        when(foodPostService.getPublicList(filter, pageable)).thenReturn(page);

        ResponseEntity<Page<FoodPostResponse>> result = controller.getPublicList(filter, pageable);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(page, result.getBody());
    }

    @Test
    void getDetail_returns200() {
        FoodPostResponse response = mockPostResponse(10L);
        when(foodPostService.getDetail(10L)).thenReturn(response);

        ResponseEntity<FoodPostResponse> result = controller.getDetail(10L);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(response, result.getBody());
    }

    @Test
    void getMyPosts_returns200() throws PermissionException {
        Pageable pageable = PageRequest.of(0, 20);
        Page<FoodPostResponse> page = new PageImpl<>(List.of(mockPostResponse(1L)));
        when(foodPostService.getMyPosts(PostStatus.AVAILABLE, "rice", pageable)).thenReturn(page);

        ResponseEntity<Page<FoodPostResponse>> result = controller.getMyPosts(PostStatus.AVAILABLE, "rice", pageable);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(page, result.getBody());
    }

    @Test
    void create_returns201() throws PermissionException {
        CreateFoodPostRequest request = new CreateFoodPostRequest();
        FoodPostResponse response = mockPostResponse(2L);
        when(foodPostService.create(request)).thenReturn(response);

        ResponseEntity<FoodPostResponse> result = controller.create(request);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertSame(response, result.getBody());
    }

    @Test
    void publish_returns200() throws PermissionException {
        FoodPostResponse response = mockPostResponse(2L);
        when(foodPostService.publish(2L)).thenReturn(response);

        ResponseEntity<FoodPostResponse> result = controller.publish(2L);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(response, result.getBody());
    }

    @Test
    void update_returns200() throws PermissionException {
        UpdateFoodPostRequest request = new UpdateFoodPostRequest();
        FoodPostResponse response = mockPostResponse(2L);
        when(foodPostService.update(2L, request)).thenReturn(response);

        ResponseEntity<FoodPostResponse> result = controller.update(2L, request);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(response, result.getBody());
    }

    @Test
    void hide_returns200() throws PermissionException {
        FoodPostResponse response = mockPostResponse(2L);
        when(foodPostService.hide(2L)).thenReturn(response);

        ResponseEntity<FoodPostResponse> result = controller.hide(2L);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(response, result.getBody());
    }

    @Test
    void unhide_returns200() throws PermissionException {
        FoodPostResponse response = mockPostResponse(2L);
        when(foodPostService.unhide(2L)).thenReturn(response);

        ResponseEntity<FoodPostResponse> result = controller.unhide(2L);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(response, result.getBody());
    }

    @Test
    void cancel_returns200() throws PermissionException {
        FoodPostResponse response = mockPostResponse(2L);
        when(foodPostService.cancel(2L)).thenReturn(response);

        ResponseEntity<FoodPostResponse> result = controller.cancel(2L);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(response, result.getBody());
    }

    @Test
    void getDetailForOwner_returns200() throws PermissionException {
        FoodPostResponse response = mockPostResponse(2L);
        when(foodPostService.getDetailForOwner(2L)).thenReturn(response);

        ResponseEntity<FoodPostResponse> result = controller.getDetailForOwner(2L);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(response, result.getBody());
    }
}
