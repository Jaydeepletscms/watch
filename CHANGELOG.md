# Watch Party Prototype — Build Changelog

## Step 1: Project Setup, Theme, Navigation & Home Screen
- **Built:**
  - Configured Material 3 Dark Theme (`#0F1115` background, `#1A1D24` surface, `#232733` card, `#7C5CFF` purple primary, `#26D0CE` teal secondary, `#FF5C7A` error, `#3DDC97` success, 16dp cards, 12dp buttons).
  - Created custom adaptive launcher icon and `AppNav.kt` navigation graph (`home`, `host_setup`, `lobby`, `join`, `qr_scan`, `room`, `ended`, `local_demo`).
  - Built `HomeScreen.kt` and `HomeViewModel.kt` with min 2-character name validation, `Create Party` / `Join Party` buttons, and Wi-Fi status footer.
- **Tested:** Name validation disables `Create Party` and `Join Party` until `name.trim().length >= 2`.

## Step 2: Chunker + ChunkPlayer (Local 3-Chunk Gapless Playback)
- **Built:**
  - `Chunker.kt` using `MediaExtractor` + `MediaMuxer` on `Dispatchers.IO`. Validates `video/avc` (H.264) and `audio/mp4a-latm` (AAC), aligns cuts to sync keyframes (`SEEK_TO_NEXT_SYNC`), rebases sample timestamps from `0` per chunk, and keeps audio samples within each chunk's window for tight lip-sync.
  - `ChunkPlayer.kt` wrapping Media3 `ExoPlayer` in playlist mode with early `prepare()`, global position calculation (`chunk.startMs + currentPosition`), and inter-chunk transition gap measurement (`<150 ms`).
  - `SampleVideoGenerator.kt` using `MediaCodec` + `MediaMuxer` to synthesize a 30s H.264/AAC MP4 on-device so single-device and emulator environments can test without external files.
  - `LocalDemoScreen.kt` for 1-tap verification of 3-chunk extraction, SHA-256 hashing, and gapless playback.
- **Tested:** 3 chunks generated and queued into ExoPlayer playlist with gapless transition (`<150 ms`).

## Step 3: Protocol, HostServer, ClientConnection, Lobby, QR Create/Scan & Join
- **Built:**
  - `Protocol.kt` with `kotlinx.serialization` JSON messages (`join`, `welcome`, `reject`, `participants`, `chunk_meta`, `progress`, `ready`, `waiting`, `play_at`, `pause`, `resume`, `sync`, `time_req`, `time_res`, `chunk_done`, `resend`, `chat`, `reaction`, `kick`, `mute`, `seek`, `ended`) and 8-byte big-endian binary frame encoder/decoder (`chunkNo`, `frameIndex`, 256 KB payload).
  - `HostServer.kt` using `org.java-websocket:Java-WebSocket` on port `8080` with max 6 participants enforcement (`reject("full")`), room lock enforcement (`reject("locked")`), and sender privilege validation.
  - `ClientConnection.kt` using `OkHttp` WebSocket client.
  - `Qr.kt`, `LobbyScreen.kt`, `JoinScreen.kt`, and `QrScanScreen.kt` (ZXing QR generator + CameraX & ML Kit Barcode Scanning).
- **Tested:** Host starts on port 8080, generates QR code, loopback/remote clients join, and real-time participant list updates across devices.

## Step 4: ChunkSender + ChunkReceiver, SHA-256 Verification & Auto-Delete
- **Built:**
  - `ChunkSender.kt` streaming 256 KB binary frames with `yield()` between frames so control messages interleave without delay.
  - `ChunkReceiver.kt` writing frames at `frameIndex * 262144` via `RandomAccessFile`, reporting `progress` every 5%, verifying total byte size and SHA-256, renaming `.part` to `.mp4`, sending `ready(chunkNo)`, requesting `resend(chunkNo)` up to 2 times on mismatch, and deleting chunk `N-1` after chunk `N` plays.
  - `ChunkStore.kt` managing `cacheDir/host_chunks` and `cacheDir/chunks` with `.nomedia` marker files.
- **Tested:** Binary frame reassembly matches SHA-256 digest and old chunks are deleted after playback.

## Step 5: ClockSync + SyncEngine (READY, play_at, Pause, Resume, Waiting Banner)
- **Built:**
  - `ClockSync.kt` taking 8 `time_req` / `time_res` samples (200ms interval), selecting minimum RTT offset, and refreshing every 60s.
  - `SyncEngine.kt` implementing participant state machine (`IDLE`, `LOADING`, `READY_WAIT`, `PLAYING`, `BUFFERING`, `PAUSED`, `ENDED`), `waiting` broadcast every 1s, `Start anyway` override, `play_at(now + 2000)` with `3-2-1` countdown overlay, coroutine `delay(delay - 20)` + final 20ms busy-wait start, and synchronized `pause` / `resume`.
- **Tested:** Master-participant clock offset calculation and synchronized `play_at` execution.

## Step 6: Continuous Pipeline, Lead Buffer & 3-Tier Drift Correction
- **Built:**
  - Bounded 2-chunk lead buffer in `Chunker` + `SyncEngine`, pre-end buffer check 3 seconds before chunk `N` ends, and 5-second `sync(posMs, hostMs)` broadcast with 3-tier drift correction (`<100ms` ignore, `100-500ms` `0.97x`/`1.03x` speed adjustment until `<40ms`, `>500ms` `seekTo(expected)`).
- **Tested:** Continuous multi-chunk pipeline and drift recovery.

## Step 7: Chat, Floating Reactions, Host Controls & Landscape Mode
- **Built:**
  - `ChatPanel.kt`, `ReactionsBar.kt` (`FloatingReactionsOverlay` with 3/sec rate limit), `ParticipantsStrip.kt` (with Host kick/mute controls), and responsive `RoomScreen.kt` supporting both Portrait and full-screen Landscape with swipeable/toggleable chat overlay.
- **Tested:** Live chat relay, rate-limited floating reactions, room lock/unlock, kick/mute, and landscape toggle.

## Step 8: HostForegroundService, Battery/Temperature Warning, Error States & Cleanup
- **Built:**
  - `HostForegroundService.kt` (`foregroundServiceType="dataSync"`, ongoing notification, `PARTIAL_WAKE_LOCK`).
  - Live battery & temperature indicator in `RoomScreen` (`Net.getDeviceHealth`), storage check (`StatFs`), and automatic cache cleanup on app launch, leave, or `EndedScreen`.
- **Tested:** Foreground service lifecycle and cache directory cleanup.

## Step 9 (Stretch Goal): Synchronized Host Seek
- **Built:**
  - Host seek slider in `RoomScreen.kt` + `SyncEngine.hostSeekTo()` which broadcasts `seek(targetPosMs)` and restarts `Chunker` from the nearest keyframe at `targetPosMs`.
- **Tested:** Seek restarts chunk pipeline and resynchronizes participants.
