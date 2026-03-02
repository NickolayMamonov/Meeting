package dev.whysoezzy.meet.navigation

sealed class MeetRoute(val route: String) {
    // Auth routes
    object Auth : MeetRoute("auth")
    object PhoneInput : MeetRoute("auth/phone")
    object CodeVerification : MeetRoute("auth/code/{phoneNumber}") {
        fun createRoute(phoneNumber: String) = "auth/code/${encode(phoneNumber)}"
    }
    object NameInput : MeetRoute("auth/name/{phone}/{code}") {
        fun createRoute(phone: String, code: String) = "auth/name/${encode(phone)}/$code"
    }
    object AuthSuccess : MeetRoute("auth/success")

    // Main routes
    object Main : MeetRoute("main")
    object MeetingDetails : MeetRoute("meeting/{meetingId}") {
        fun createRoute(meetingId: Long) = "meeting/$meetingId"
    }
    object MeetingParticipants : MeetRoute("meeting/{meetingId}/participants") {
        fun createRoute(meetingId: Long) = "meeting/$meetingId/participants"
    }

    // Communities routes
    object Communities : MeetRoute("communities")
    object CommunityDetails : MeetRoute("community/{communityId}") {
        fun createRoute(communityId: Long) = "community/$communityId"
    }
    object CommunitySubscribers : MeetRoute("community/{communityId}/subscribers") {
        fun createRoute(communityId: Long) = "community/$communityId/subscribers"
    }

    // Profile routes
    object Profile : MeetRoute("profile")
    object ProfileEdit : MeetRoute("profile/edit")
    object UserProfile : MeetRoute("profile/{userId}") {
        fun createRoute(userId: Long) = "profile/$userId"
    }

    companion object {
        // URL-кодирование для телефонного номера (+7 → %2B7 и т.д.)
        fun encode(value: String): String =
            java.net.URLEncoder.encode(value, "UTF-8")

        fun decode(value: String): String =
            java.net.URLDecoder.decode(value, "UTF-8")
    }
}
