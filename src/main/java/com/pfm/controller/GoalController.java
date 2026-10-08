package com.pfm.controller;

import com.pfm.dto.request.GoalCreateRequest;
import com.pfm.dto.request.GoalUpdateRequest;
import com.pfm.dto.response.GoalListResponse;
import com.pfm.dto.response.GoalResponse;
import com.pfm.dto.response.MessageResponse;
import com.pfm.service.GoalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for savings goal management. Thin by design: all validation beyond
 * {@code @Valid} and all business rules (including the 404-vs-403 ownership policy) live in
 * {@link GoalService}; errors are translated centrally by the global exception handler.
 */
@RestController
@RequestMapping("/api/goals")
public class GoalController {

    private final GoalService goalService;

    /**
     * Creates the controller.
     *
     * @param goalService the goal service
     */
    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    /**
     * Creates a savings goal owned by the authenticated caller.
     *
     * @param request the validated create payload
     * @return 201 with the created goal including its initial progress
     */
    @PostMapping
    public ResponseEntity<GoalResponse> createGoal(@Valid @RequestBody GoalCreateRequest request) {
        GoalResponse created = goalService.createGoal(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Lists the authenticated caller's goals.
     *
     * @return 200 with the caller's goals, each including recomputed progress
     */
    @GetMapping
    public ResponseEntity<GoalListResponse> listGoals() {
        return ResponseEntity.ok(new GoalListResponse(goalService.listGoals()));
    }

    /**
     * Reads one goal.
     *
     * @param id the goal id
     * @return 200 with the goal including recomputed progress
     */
    @GetMapping("/{id}")
    public ResponseEntity<GoalResponse> getGoal(@PathVariable Long id) {
        return ResponseEntity.ok(goalService.getGoal(id));
    }

    /**
     * Updates a goal's target amount and/or target date.
     *
     * @param id      the goal id
     * @param request the validated update payload
     * @return 200 with the goal recalculated after the update
     */
    @PutMapping("/{id}")
    public ResponseEntity<GoalResponse> updateGoal(@PathVariable Long id,
                                                   @Valid @RequestBody GoalUpdateRequest request) {
        return ResponseEntity.ok(goalService.updateGoal(id, request));
    }

    /**
     * Deletes a goal.
     *
     * @param id the goal id
     * @return 200 with a confirmation message
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponse> deleteGoal(@PathVariable Long id) {
        goalService.deleteGoal(id);
        return ResponseEntity.ok(new MessageResponse("Goal deleted successfully"));
    }
}
