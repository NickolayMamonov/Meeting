package com.whysoezzy.data

internal object CommunitiesTestFixtures {
    const val COMMUNITY_ID = 41L

    val communityJson =
        """
        {
          "id": 41,
          "name": "Compiler Club",
          "description": "A synthetic community",
          "imageUrl": "https://example.test/community.png",
          "subscribersCount": 128,
          "isSubscribed": true,
          "tags": [
            {
              "id": 7,
              "text": "Kotlin",
              "state": "SELECTED"
            }
          ],
          "futureField": "ignored"
        }
        """.trimIndent()

    val meetingJson =
        """
        {
          "id": 73,
          "imageUrl": "https://example.test/meeting.png",
          "title": "Compiler internals",
          "description": "A synthetic meeting",
          "time": 1712345678,
          "date": "2030-04-05",
          "address": {
            "address": "Test Hall",
            "latitude": 55.75,
            "longitude": 37.61
          },
          "tags": [
            {
              "id": 9,
              "text": "Compilers"
            }
          ],
          "personHost": {
            "id": 11,
            "name": "Ada",
            "surname": "Lovelace",
            "description": "Host bio",
            "imageUrl": "https://example.test/ada.png"
          },
          "communityHost": {
            "id": 41,
            "title": "Compiler Club",
            "description": "Host community",
            "imageUrl": "https://example.test/community.png",
            "meetingsInfo": [
              {
                "id": 74,
                "title": "Next compiler meeting",
                "imageUrl": "https://example.test/next.png",
                "date": "2030-05-06"
              }
            ]
          },
          "participants": [
            {
              "id": 12,
              "name": "Grace",
              "surname": "Hopper",
              "imageUrl": "https://example.test/grace.png",
              "bio": "Participant bio",
              "role": "MEMBER"
            }
          ],
          "meetingStatus": "FINISHED",
          "isUserInParticipants": true,
          "capacity": null,
          "source": "EXTERNAL",
          "externalUrl": "https://example.test/register",
          "isOnline": true
        }
        """.trimIndent()

    val subscriberJson =
        """
        {
          "id": 12,
          "name": "Grace",
          "surname": "Hopper",
          "avatarUrl": "https://example.test/grace.png",
          "bio": "Participant bio",
          "role": "MEMBER"
        }
        """.trimIndent()

    fun errorEnvelope(
        status: Int,
        code: String,
    ): String =
        """
        {
          "status": $status,
          "message": "Synthetic backend message",
          "timestamp": "2030-01-01T00:00:00Z",
          "path": "/communities/$COMMUNITY_ID",
          "code": "$code"
        }
        """.trimIndent()
}
