package com.pfm.dto.response;

import java.util.List;

/**
 * Wrapper for {@code GET /api/goals}.
 *
 * @param goals the caller's goals
 */
public record GoalListResponse(List<GoalResponse> goals) {
}
