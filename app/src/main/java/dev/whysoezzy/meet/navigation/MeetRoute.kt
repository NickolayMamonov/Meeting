package dev.whysoezzy.meet.navigation

sealed class MeetRoute(val route: String) {
    // Auth routes
    object Auth : MeetRoute("auth")
    object PhoneInput : MeetRoute("auth/phone")
    object CodeVerification : MeetRoute("auth/code/{phoneNumber}") {
        fun createRoute(phoneNumber: String) = "auth/code/$phoneNumber"
    }

    object NameInput : MeetRoute("auth/name")
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
}