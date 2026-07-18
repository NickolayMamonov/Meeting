package com.whysoezzy.data.api

import com.whysoezzy.data.dto.CommunityInfoDto
import com.whysoezzy.data.dto.MeetingInfoDto
import com.whysoezzy.data.dto.AvatarUploadResponseDto
import com.whysoezzy.data.dto.UpdateUserDto
import com.whysoezzy.data.dto.UserProfileDto
import com.whysoezzy.domain.models.AvatarUpload
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.MultiPartFormDataContent
import kotlinx.io.asSource
import kotlinx.io.buffered

internal class UserApiKtor(
    private val client: HttpClient,
) : UserApi {
    override suspend fun getCurrentUserProfile(): UserProfileDto {
        return client.get("profile").body()
    }

    override suspend fun getUserProfile(id: Long): UserProfileDto {
        return client.get("users/$id").body()
    }

    override suspend fun updateUserProfile(updateDto: UpdateUserDto): UserProfileDto {
        return client
            .put("profile") {
                contentType(ContentType.Application.Json)
                setBody(updateDto)
            }.body()
    }

    override suspend fun uploadAvatar(
        upload: AvatarUpload,
        onProgress: (sentBytes: Long, totalBytes: Long) -> Unit,
    ): AvatarUploadResponseDto {
        return client.post("media/avatar") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        appendInput(
                            key = "file",
                            headers = Headers.build {
                                append(HttpHeaders.ContentType, upload.contentType)
                                append(
                                    HttpHeaders.ContentDisposition,
                                    "filename=\"${upload.fileName}\"",
                                )
                            },
                            size = upload.contentLength,
                        ) {
                            upload.openStream().asSource().buffered()
                        }
                    },
                ),
            )
            onUpload { sentBytes, totalBytes ->
                onProgress(sentBytes, totalBytes ?: upload.contentLength)
            }
        }.body()
    }

    override suspend fun deleteCurrentUserProfile() {
        client.delete("profile") {
            expectSuccess = true
        }
    }

    override suspend fun getUserMeetings(userId: Long): List<MeetingInfoDto> {
        return client.get("users/$userId/meetings").body()
    }

    override suspend fun getUserCommunities(userId: Long): List<CommunityInfoDto> {
        return client.get("users/$userId/communities").body()
    }
}
