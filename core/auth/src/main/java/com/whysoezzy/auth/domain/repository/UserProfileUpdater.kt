package com.whysoezzy.auth.domain.repository

import com.whysoezzy.auth.domain.models.AuthOutcome

interface UserProfileUpdater {
    suspend fun updateName(
        name: String,
        surname: String,
    ): AuthOutcome<Unit>
}
