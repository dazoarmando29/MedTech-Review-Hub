package io.medtech.backend.service

import io.medtech.backend.domain.ApiException
import io.medtech.backend.domain.PlanDay
import io.medtech.backend.domain.StudyPlan
import io.medtech.backend.repository.PlanRepository
import io.medtech.backend.repository.ProfileRepository

class PlanService(
    private val plans: PlanRepository = PlanRepository(),
    private val profiles: ProfileRepository = ProfileRepository(),
    private val contentService: ContentService,
) {

    suspend fun getOrGenerate(userId: String): StudyPlan {
        plans.latestForUser(userId)?.let { return it }
        return regenerate(userId)
    }

    suspend fun regenerate(userId: String): StudyPlan {
        val profile = profiles.get(userId) ?: io.medtech.backend.domain.Profile()
        val generated = PlanGenerator.generate(profile, contentService.subjects())
        return plans.save(userId, generated.days, generated.weightsJson, generated.label)
    }

    suspend fun replace(userId: String, days: List<PlanDay>): StudyPlan {
        if (days.isEmpty()) throw ApiException.validation("days must not be empty")
        days.forEach { day ->
            day.items.forEach { item ->
                if (item.targetMinutes <= 0 || item.targetMinutes > 24 * 60) {
                    throw ApiException.validation("targetMinutes must be between 1 and 1440")
                }
            }
        }
        return plans.save(userId, days, "{}", "Custom plan")
    }
}
