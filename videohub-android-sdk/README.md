# VideoHub Android SDK v1.18.0

Official VideoHub client SDK for Android. Built on LiveKit (used internally, not exposed).

## Install

```kotlin
// settings.gradle.kts
include(":videohub-android")
project(":videohub-android").projectDir = file("../videohub-android")

// app/build.gradle.kts
implementation(project(":videohub-android"))
```

Or via Maven:
```kotlin
implementation("dev.videohub:videohub-android:1.18.0")
```

## Usage

```kotlin
import dev.videohub.VideoHubSDK
import dev.videohub.utils.VideoHubConfig

// 1. Initialize
val config = VideoHubConfig(
    api_key = "vhub_live_xxx",
    app_id = "xxx",
    app_secret = "xxx",
    app_identifier = "com.your.app",
    app_platform = "Android",
    app_name = "My App",
    domain = "api.videohub.dev",
    username = "your-rtm-username",
    password = "your-rtm-password"
)
VideoHubSDK.create(context, config)

// 2. Join room (token minted internally)
val session = VideoHubSDK.videoHub
    .withNoiseCancellation(true)
    .join(roomName = "my-room", audio = false, video = false)

// 3. Observe events
session.observeEvents().collect { event ->
    when (event) {
        is VideoHubEvent.Reconnecting -> println("Reconnecting...")
        is VideoHubEvent.Reconnected -> println("Reconnected!")
        is VideoHubEvent.ParticipantJoined -> println("Joined: ${event.identity}")
        else -> {}
    }
}

// 4. Observe active speakers
session.activeSpeakers.collect { speakers ->
    println("Active: $speakers")
}

// 5. Mic control
session.local.setMicrophoneEnabled(true)
session.local.publishAudioStream(withMicrophone = true)

// 6. RTM (chat/messaging/moderation)
val rtm = VideoHubSDK.videoHub.getRTM()
rtm?.moderation?.muteUser(roomId = "my-room", targetUserId = "user-123")
rtm?.moderation?.kickUser(roomId = "my-room", targetUserId = "user-123")
rtm?.chat?.send(roomId = "my-room", message = "Hello!")

// 7. Leave
VideoHubSDK.videoHub.leaveAllRooms()
```

## API Reference

### VideoHubSDK (singleton)
| Method | Description |
|---|---|
| `create(context, config)` | Initialize SDK |
| `videoHub` | Get VideoHub instance |
| `isInitialized()` | Check if initialized |

### VideoHub
| Method | Description |
|---|---|
| `withNoiseCancellation(enabled)` | Enable noise cancellation |
| `join(roomName, audio, video)` | Join room (token minted internally) |
| `getRTM()` | Get RTM instance for chat/moderation |
| `leaveAllRooms()` | Leave all active rooms |
| `createRoom(roomName, mode, max)` | Create room via REST API |

### RoomSession
| Property/Method | Description |
|---|---|
| `observeEvents()` | Flow of VideoHubEvent |
| `activeSpeakers` | Flow of active speaker IDs |
| `local` | LocalParticipant (mic/camera control) |
| `setMicrophoneEnabled(bool)` | Toggle mic |
| `publishAudioStream(withMic)` | Publish audio |
| `disconnect()` | Leave room |

### RTM
| Sub-module | Methods |
|---|---|
| `moderation` | `muteUser(roomId, userId)`, `kickUser(roomId, userId)` |
| `chat` | `send(roomId, msg)`, `typingStart/Stop(roomId)`, `deleteMessage(roomId, id)` |
| `presence` | `joinRoom(roomId)`, `leaveRoom(roomId)` |

## Permissions

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
```
