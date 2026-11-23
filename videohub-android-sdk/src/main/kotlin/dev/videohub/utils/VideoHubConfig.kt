package dev.videohub.utils

/**
 * VideoHub SDK Configuration.
 *
 * Matches the official VideoHub Android SDK v1.18.0 API exactly.
 * Contains all fields the Watch Party app expects.
 *
 * @param api_key       VideoHub API key (from dashboard)
 * @param app_id        VideoHub App ID
 * @param app_secret    VideoHub App Secret
 * @param app_identifier App bundle identifier (e.g. "com.letscms.bindaswatch")
 * @param app_platform  Platform ("Android", "web", etc.)
 * @param app_name      Human-readable app name
 * @param domain        VideoHub domain (e.g. "api.videohub.dev")
 * @param username      RTM auth username
 * @param password      RTM auth password
 * @param debug         Enable debug logging
 */
data class VideoHubConfig(
    val api_key: String = "",
    val app_id: String = "",
    val app_secret: String = "",
    val app_identifier: String = "",
    val app_platform: String = "Android",
    val app_name: String = "",
    val domain: String = "api.videohub.dev",
    val username: String = "",
    val password: String = "",
    val debug: Boolean = false,
) {
    /** Base REST API URL — hardcoded */
    val baseUrl: String get() = "https://api.videohub.dev/v1"

    /** WebSocket RTC URL — hardcoded to VideoHub media server */
    val rtcUrl: String get() = "wss://streaming.askjitendra.com"

    /** RTM WebSocket URL — hardcoded to VideoHub messaging server */
    val rtmUrl: String get() = "wss://rtms.videohub.dev/ws"

    /** Auth headers for REST API calls */
    val authHeaders: Map<String, String> get() = mapOf(
        "X-API-Key" to api_key,
        "X-App-ID" to app_id,
        "X-App-Secret" to app_secret,
        "X-App-Platform" to app_platform,
        "X-App-Identifier" to app_identifier,
        "X-App-Name" to app_name,
    )

    fun isValid(): Boolean = api_key.isNotBlank() && app_id.isNotBlank() && app_secret.isNotBlank()
}
