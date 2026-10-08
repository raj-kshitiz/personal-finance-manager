package com.pfm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pfm.dto.request.GoalUpdateRequest;
import com.pfm.dto.response.GoalResponse;
import com.pfm.exception.BadRequestException;
import com.pfm.exception.ForbiddenOperationException;
import com.pfm.exception.ResourceNotFoundException;
import com.pfm.service.GoalService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code @WebMvcTest} slice tests for {@link GoalController}. The service layer is mocked so only
 * HTTP wiring (status codes, JSON shape, request validation) is exercised.
 */
@WebMvcTest(GoalController.class)
@AutoConfigureMockMvc(addFilters = false)
class GoalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GoalService goalService;

    private static GoalResponse response(Long id, double progressPercentage) {
        return new GoalResponse(id, "Emergency Fund", new BigDecimal("10000.00"), LocalDate.of(2027, 1, 1),
                LocalDate.of(2024, 1, 1), new BigDecimal("6550.00"), progressPercentage,
                new BigDecimal("3450.00"));
    }

    @Test
    void createGoalReturns201WithProgressFields() throws Exception {
        when(goalService.createGoal(any())).thenReturn(response(1L, 65.5));

        mockMvc.perform(post("/api/goals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"goalName":"Emergency Fund","targetAmount":10000.00,"targetDate":"2027-01-01",
                                "startDate":"2024-01-01"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.currentProgress").value(6550.00))
                .andExpect(jsonPath("$.progressPercentage").value(65.5))
                .andExpect(jsonPath("$.remainingAmount").value(3450.00));
    }

    @Test
    void createGoalWithBlankNameReturns400() throws Exception {
        mockMvc.perform(post("/api/goals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goalName\":\"\",\"targetAmount\":1000.00,\"targetDate\":\"2027-01-01\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createGoalWithZeroAmountReturns400() throws Exception {
        mockMvc.perform(post("/api/goals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goalName\":\"Zero Goal\",\"targetAmount\":0,\"targetDate\":\"2026-01-01\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createGoalWithNegativeAmountReturns400() throws Exception {
        mockMvc.perform(post("/api/goals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"goalName\":\"Negative Goal\",\"targetAmount\":-1000.00,"
                                        + "\"targetDate\":\"2026-01-01\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createGoalWithPastTargetDateReturns400FromService() throws Exception {
        when(goalService.createGoal(any())).thenThrow(new BadRequestException("targetDate must be in the future"));

        mockMvc.perform(post("/api/goals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goalName\":\"Invalid Goal\",\"targetAmount\":5000.00,"
                                + "\"targetDate\":\"2023-01-01\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createGoalWithMissingTargetDateReturns400() throws Exception {
        mockMvc.perform(post("/api/goals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goalName\":\"No Target Date\",\"targetAmount\":5000.00}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listGoalsReturns200WithGoalsArray() throws Exception {
        when(goalService.listGoals()).thenReturn(List.of(response(1L, 65.5), response(2L, 50.0)));

        mockMvc.perform(get("/api/goals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goals", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.goals[0].id").value(1))
                .andExpect(jsonPath("$.goals[1].id").value(2));
    }

    @Test
    void getGoalReturns200() throws Exception {
        when(goalService.getGoal(1L)).thenReturn(response(1L, 65.5));

        mockMvc.perform(get("/api/goals/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progressPercentage").value(65.5));
    }

    @Test
    void getGoalWithNonNumericIdReturns400() throws Exception {
        mockMvc.perform(get("/api/goals/abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getGoalNotFoundReturns404() throws Exception {
        when(goalService.getGoal(999L)).thenThrow(new ResourceNotFoundException("Goal 999 not found"));

        mockMvc.perform(get("/api/goals/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getForeignGoalReturns403() throws Exception {
        when(goalService.getGoal(5L)).thenThrow(new ForbiddenOperationException("Goal 5 belongs to another user"));

        mockMvc.perform(get("/api/goals/5"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateGoalReturns200() throws Exception {
        when(goalService.updateGoal(eq(1L), any(GoalUpdateRequest.class))).thenReturn(response(1L, 60.33));

        mockMvc.perform(put("/api/goals/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetAmount\":15000.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progressPercentage").value(60.33));

        verify(goalService).updateGoal(eq(1L), any(GoalUpdateRequest.class));
    }

    @Test
    void updateGoalNotFoundReturns404() throws Exception {
        when(goalService.updateGoal(eq(999L), any(GoalUpdateRequest.class)))
                .thenThrow(new ResourceNotFoundException("Goal 999 not found"));

        mockMvc.perform(put("/api/goals/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetAmount\":5000.00}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateForeignGoalReturns403() throws Exception {
        when(goalService.updateGoal(eq(5L), any(GoalUpdateRequest.class)))
                .thenThrow(new ForbiddenOperationException("Goal 5 belongs to another user"));

        mockMvc.perform(put("/api/goals/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetAmount\":5000.00}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateGoalWithEmptyBodyReturns400FromService() throws Exception {
        when(goalService.updateGoal(eq(1L), any(GoalUpdateRequest.class)))
                .thenThrow(new BadRequestException("At least one of targetAmount or targetDate must be supplied"));

        mockMvc.perform(put("/api/goals/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateGoalWithZeroAmountReturns400() throws Exception {
        mockMvc.perform(put("/api/goals/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetAmount\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteGoalReturns200WithMessage() throws Exception {
        mockMvc.perform(delete("/api/goals/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Goal deleted successfully"));

        verify(goalService).deleteGoal(1L);
    }

    @Test
    void deleteGoalNotFoundReturns404() throws Exception {
        org.mockito.Mockito.doThrow(new ResourceNotFoundException("Goal 999 not found"))
                .when(goalService).deleteGoal(999L);

        mockMvc.perform(delete("/api/goals/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteForeignGoalReturns403() throws Exception {
        org.mockito.Mockito.doThrow(new ForbiddenOperationException("Goal 5 belongs to another user"))
                .when(goalService).deleteGoal(5L);

        mockMvc.perform(delete("/api/goals/5"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteGoalWithNonNumericIdReturns400() throws Exception {
        mockMvc.perform(delete("/api/goals/abc"))
                .andExpect(status().isBadRequest());
    }
}
