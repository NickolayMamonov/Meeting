package com.whysoezzy.data

import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf

internal val jsonHeaders: Headers =
    headersOf(
        HttpHeaders.ContentType,
        ContentType.Application.Json.toString(),
    )

internal const val meetingJson = """
{
  "id": 42,
  "imageUrl": "https://cdn.example/meetings/42.webp",
  "title": "Kotlin Coroutines",
  "description": "Structured concurrency in practice",
  "time": 1735689600,
  "date": "2025-01-01",
  "address": {
    "address": "1 Main Street",
    "latitude": 55.7558,
    "longitude": 37.6173
  },
  "tags": [
    {"id": 7, "text": "Kotlin"},
    {"id": 8, "text": "Android"}
  ],
  "personHost": {
    "id": 9,
    "name": "Ada",
    "surname": "Lovelace",
    "description": "Host bio",
    "imageUrl": "https://cdn.example/people/9.webp"
  },
  "communityHost": {
    "id": 10,
    "title": "Android Guild",
    "description": "Mobile engineers",
    "imageUrl": "https://cdn.example/communities/10.webp",
    "meetingsInfo": [
      {
        "id": 11,
        "title": "Compose",
        "imageUrl": "https://cdn.example/meetings/11.webp",
        "date": "2025-02-02"
      }
    ]
  },
  "participants": [
    {
      "id": 12,
      "name": "Grace",
      "surname": "Hopper",
      "imageUrl": null,
      "bio": null,
      "role": ""
    }
  ],
  "meetingStatus": "FULL",
  "isUserInParticipants": true,
  "capacity": null,
  "source": "TIMEPAD",
  "externalUrl": "https://events.example/42",
  "isOnline": true
}
"""

internal const val participantsJson = """
[
  {
    "id": 21,
    "name": "Linus",
    "surname": "Torvalds",
    "avatarUrl": "https://cdn.example/people/21.webp",
    "bio": "Kernel engineer",
    "role": "SPEAKER"
  }
]
"""

internal const val adsJson = """
[
  {
    "type": "COMMUNITIES",
    "id": 31,
    "isActive": true,
    "title": "Meet communities",
    "description": "Find your people",
    "communities": [
      {
        "id": 32,
        "name": "Kotlin User Group",
        "description": null,
        "imageUrl": "https://cdn.example/communities/32.webp",
        "subscribersCount": null,
        "isSubscribed": true
      }
    ]
  },
  {
    "type": "TEXT",
    "id": 33,
    "isActive": false,
    "title": "Conference",
    "description": "Early bird tickets",
    "actionText": "Open",
    "actionUrl": "https://events.example/conf"
  },
  {
    "type": "PEOPLE",
    "id": 34,
    "isActive": true,
    "title": "Featured speaker",
    "description": "Follow an expert",
    "users": [
      {
        "id": 35,
        "name": "Barbara",
        "surname": "Liskov",
        "avatarUrl": "https://cdn.example/people/35.webp",
        "bio": "Computer scientist",
        "role": "SPEAKER"
      }
    ]
  }
]
"""

internal fun errorJson(
    status: Int,
    code: String,
): String = """
{
  "status": $status,
  "message": "Synthetic backend failure",
  "timestamp": "2026-08-10T00:00:00Z",
  "path": "/meetings/42",
  "code": "$code"
}
"""
