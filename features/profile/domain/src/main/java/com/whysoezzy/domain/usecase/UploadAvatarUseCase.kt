package com.whysoezzy.domain.usecase

import com.whysoezzy.domain.models.AvatarUpload
import com.whysoezzy.domain.repository.UserRepository

class UploadAvatarUseCase(
    private val repository: UserRepository,
) {
    suspend operator fun invoke(
        upload: AvatarUpload,
        onProgress: (sentBytes: Long, totalBytes: Long) -> Unit,
    ): Result<String> = repository.uploadAvatar(upload, onProgress)
}
