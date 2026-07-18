package com.whysoezzy.domain.models

import java.io.InputStream

data class AvatarUpload(
    val fileName: String,
    val contentType: String,
    val contentLength: Long,
    val openStream: () -> InputStream,
)
