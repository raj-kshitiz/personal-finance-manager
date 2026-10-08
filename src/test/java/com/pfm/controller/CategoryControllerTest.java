package com.pfm.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pfm.dto.request.CategoryCreateRequest;
import com.pfm.dto.response.CategoryResponse;
import com.pfm.entity.CategoryType;
import com.pfm.exception.BadRequestException;
import com.pfm.exception.ConflictException;
import com.pfm.exception.ForbiddenOperationException;
import com.pfm.exception.ResourceNotFoundException;
import com.pfm.service.CategoryService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code @WebMvcTest} slice tests for {@link CategoryController}. The service layer is mocked so
 * only HTTP wiring (status codes, JSON shape, request validation) is exercised.
 */
@WebMvcTest(CategoryController.class)
@AutoConfigureMockMvc(addFilters = false)
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;

    @Test
    void listCategoriesReturns200WithBothCustomSpellings() throws Exception {
        CategoryResponse salary = new CategoryResponse(1L, "Salary", CategoryType.INCOME, false);
        CategoryResponse freelance = new CategoryResponse(10L, "Freelance", CategoryType.INCOME, true);
        when(categoryService.listCategories()).thenReturn(List.of(salary, freelance));

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.categories[0].name", is("Salary")))
                .andExpect(jsonPath("$.categories[0].isCustom", is(false)))
                .andExpect(jsonPath("$.categories[0].custom", is(false)))
                .andExpect(jsonPath("$.categories[1].name", is("Freelance")))
                .andExpect(jsonPath("$.categories[1].isCustom", is(true)))
                .andExpect(jsonPath("$.categories[1].custom", is(true)));
    }

    @Test
    void createCategoryReturns201WithCreatedBody() throws Exception {
        CategoryResponse created = new CategoryResponse(5L, "Freelance", CategoryType.INCOME, true);
        when(categoryService.createCategory(any(CategoryCreateRequest.class))).thenReturn(created);

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Freelance\",\"type\":\"INCOME\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(5)))
                .andExpect(jsonPath("$.name", is("Freelance")))
                .andExpect(jsonPath("$.isCustom", is(true)))
                .andExpect(jsonPath("$.custom", is(true)));
    }

    @Test
    void createCategoryWithBlankNameReturns400() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \",\"type\":\"INCOME\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createCategoryWithInvalidTypeReturns400() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Something\",\"type\":\"NOT_A_TYPE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createCategoryConflictReturns409() throws Exception {
        when(categoryService.createCategory(any(CategoryCreateRequest.class)))
                .thenThrow(new ConflictException("You already have a category named 'Food'"));

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Food\",\"type\":\"EXPENSE\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteCategorySuccessReturns200WithMessage() throws Exception {
        mockMvc.perform(delete("/api/categories/Freelance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Category deleted successfully")));

        verify(categoryService).deleteCategory("Freelance");
    }

    @Test
    void deleteCategoryWithDotInNameIsPassedThroughIntact() throws Exception {
        mockMvc.perform(delete("/api/categories/GymMembership_1760000000"))
                .andExpect(status().isOk());

        verify(categoryService).deleteCategory("GymMembership_1760000000");
    }

    @Test
    void deleteDefaultCategoryReturns403() throws Exception {
        org.mockito.Mockito.doThrow(new ForbiddenOperationException("Default category 'Food' cannot be deleted"))
                .when(categoryService).deleteCategory("Food");

        mockMvc.perform(delete("/api/categories/Food"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteUnknownCategoryReturns404() throws Exception {
        org.mockito.Mockito.doThrow(new ResourceNotFoundException("Category 'Unknown' not found"))
                .when(categoryService).deleteCategory("Unknown");

        mockMvc.perform(delete("/api/categories/Unknown"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteInUseCategoryReturns400() throws Exception {
        org.mockito.Mockito.doThrow(new BadRequestException(
                        "Category 'GymMembership' cannot be deleted because it is referenced by existing transactions"))
                .when(categoryService).deleteCategory("GymMembership");

        mockMvc.perform(delete("/api/categories/GymMembership"))
                .andExpect(status().isBadRequest());
    }
}
