package com.example.hitmapstargets

import java.time.Instant

data class ElusiveTarget(
    val id: Long,
    val name: String,
    val missionUrl: String,
    val imageUrl: String?,
    val startsAt: Instant,
    val endsAt: Instant,
) {
    fun status(now: Instant): TargetStatus = when {
        now.isBefore(startsAt) -> TargetStatus.UPCOMING
        now.isBefore(endsAt) -> TargetStatus.ACTIVE
        else -> TargetStatus.EXPIRED
    }

    val location: String
        get() {
            val slug = missionUrl.trim('/').split('/').getOrNull(2).orEmpty()
            return when (slug) {
                "paris" -> "ПАРИЖ"
                "hawkes-bay" -> "ХОУКС-БЭЙ"
                "bangkok" -> "БАНГКОК"
                "marrakesh" -> "МАРРАКЕШ"
                "sapienza" -> "САПИЕНЦА"
                "colorado" -> "КОЛОРАДО"
                "hokkaido" -> "ХОККАЙДО"
                "miami" -> "МАЙАМИ"
                "mumbai" -> "МУМБАИ"
                "whittleton-creek" -> "УИТТЛТОН-КРИК"
                "isle-of-sgail" -> "ОСТРОВ СГЕЙЛ"
                "dubai" -> "ДУБАЙ"
                "dartmoor" -> "ДАРТМУР"
                "berlin" -> "БЕРЛИН"
                "chongqing" -> "ЧУНЦИН"
                "mendoza" -> "МЕНДОСА"
                "ambrose-island" -> "ОСТРОВ АМБРОУЗ"
                "new-york" -> "НЬЮ-ЙОРК"
                "haven-island" -> "ОСТРОВ ХЕЙВЕН"
                "santa-fortuna" -> "САНТА-ФОРТУНА"
                else -> slug.replace('-', ' ').uppercase().ifBlank { "HITMAN" }
            }
        }
}

enum class TargetStatus { UPCOMING, ACTIVE, EXPIRED }

fun formatCountdown(now: Instant, until: Instant): String {
    val seconds = (until.epochSecond - now.epochSecond).coerceAtLeast(0)
    val hours = seconds / 3600
    val minutes = seconds % 3600 / 60
    val remainder = seconds % 60
    return "%02d:%02d:%02d".format(java.util.Locale.ROOT, hours, minutes, remainder)
}
