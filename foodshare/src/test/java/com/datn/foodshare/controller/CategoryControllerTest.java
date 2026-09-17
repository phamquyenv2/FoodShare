package com.datn.foodshare.controller;

import com.datn.foodshare.domain.entity.Category;
import com.datn.foodshare.repository.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CategoryControllerTest {

    private final CategoryRepository categoryRepository = mock(CategoryRepository.class);
    private final CategoryController controller = new CategoryController(categoryRepository);

    @Test
    void testGetAllCategories_returnsCategoriesFromRepository() {
        Category c1 = Category.builder().id(1L).name("Cơm và món chính").build();
        Category c2 = Category.builder().id(2L).name("Rau củ và trái cây").build();
        when(categoryRepository.findAll()).thenReturn(List.of(c1, c2));

        ResponseEntity<?> response = controller.getAllCategories();
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());

        @SuppressWarnings("unchecked")
        var list = (List<com.datn.foodshare.domain.response.CategoryResponse>) response.getBody();
        assertEquals(2, list.size());
        assertEquals("Cơm và món chính", list.get(0).name());
        assertEquals(1L, list.get(0).id());
        assertEquals("Rau củ và trái cây", list.get(1).name());
        assertEquals(2L, list.get(1).id());
    }
}
