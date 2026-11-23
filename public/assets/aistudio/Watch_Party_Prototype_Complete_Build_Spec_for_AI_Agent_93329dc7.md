# Watch Party Prototype: Complete Build Spec for AI Agent

*Same-WiFi, no-backend Android prototype. Version 1.0. Is document ko ek AI coding agent (Claude Code, Cursor, etc.) ko dene ke liye likha gaya hai.*

## 0. Agent ke liye rules (pehle padho)

1. Is spec ko step by step follow karo (Section 14 ka build order). Ek step khatam hone par app build karo (./gradlew assembleDebug) aur us step ka acceptance check pass karo, tabhi agle step par jao.
2. Koi library ka API apne andaze se mat likho. Agar pakka nahi pata to library ka official doc/README dekho. Latest stable version use karo.
3. Jahan spec mein (DECISION) likha hai wahan default choice di gayi hai. Badalne ki zaroorat ho to ek line mein reason likho.
4. Har step ke baad ek chhota CHANGELOG.md entry likho: kya bana, kya test hua, kya baaki hai.
5. Code simple rakho: Kotlin, Coroutines, Flow, Jetpack Compose. Over-engineering nahi.
6. Agar koi cheez is spec se match nahi karti (jaise device ka limitation), to ruk kar problem likho, aur fallback suggest karo. Chupke se spec mat badlo.

## 1. Goal aur scope

**Goal:** Ek host phone par local video chunno, usko 10-30 second ke chunks mein tod kar same WiFi par 1-5 participants ke phones tak bhejo, aur sab phones par ek saath play karo. Saath mein chat aur reactions.

**Is prototype mein hai:**

- Room create (host) aur join (QR scan ya IP daalke)
- Host phone par embedded WebSocket server. Koi cloud ya backend nahi.
- Local video ko chunks mein todna (re-encode nahi)
- Chunks ko WebSocket binary frames se bhejna, SHA-256 verify
- READY, PLAY\_AT, pause, resume, buffering banner
- Clock sync (host ka clock master)
- Chat aur emoji reactions
- Participant ke phone se chunk chalne ke baad delete

**Is prototype mein nahi hai:**

- Camera/voice call, internet par chalna, reconnect, seek (stretch goal), DRM, iOS, login

**Demo ke liye target:** 1 host + 3 participants, 5-10 minute ki H.264/AAC mp4 video, ek WiFi ya host ke hotspot par.

## 2. Tech stack

| Cheez | Choice |
| --- | --- |
| Language | Kotlin |
| UI | Jetpack Compose (Material 3) |
| minSdk / targetSdk | 26 / 34 |
| Player | Media3 ExoPlayer |
| Chunking | MediaExtractor + MediaMuxer (android.media) |
| WebSocket server (host) | org.java-websocket:Java-WebSocket (DECISION) |
| WebSocket client | OkHttp |
| JSON | kotlinx.serialization |
| QR generate | com.google.zxing:core |
| QR scan | ML Kit Barcode Scanning ya CameraX + zxing (DECISION: ML Kit) |
| DI | Manual (Hilt ki zaroorat nahi) |
| Async | Coroutines + StateFlow |

## 3. Project structure

```
app/src/main/java/com/example/watchparty/
  MainActivity.kt
  navigation/        AppNav.kt
  ui/
    theme/           Color.kt, Type.kt, Theme.kt
    home/            HomeScreen.kt, HomeViewModel.kt
    host/            HostSetupScreen.kt, LobbyScreen.kt
    join/            JoinScreen.kt, QrScanScreen.kt
    room/            RoomScreen.kt, RoomViewModel.kt, ChatPanel.kt,
                     ReactionsBar.kt, ParticipantsStrip.kt, StatusBanner.kt
  net/
    Protocol.kt      (message data classes + JSON)
    HostServer.kt    (WebSocket server)
    ClientConnection.kt (OkHttp WebSocket client)
  media/
    Chunker.kt       (video to chunk files)
    ChunkSender.kt   (frames to participants)
    ChunkReceiver.kt (reassemble + sha256 verify)
    ChunkStore.kt    (temp files, delete)
    ChunkPlayer.kt   (ExoPlayer playlist)
  sync/
    ClockSync.kt
    SyncEngine.kt    (state machine)
  service/
    HostForegroundService.kt
  util/
    Net.kt (local IP), Hash.kt (sha256), Qr.kt
```

**Rule:** UI sirf ViewModel se baat kare. ViewModel SyncEngine, HostServer aur ClientConnection ko use kare. Network aur media layer UI ko nahi jaanti.

## 4. Manifest aur permissions

- INTERNET, ACCESS\_NETWORK\_STATE, ACCESS\_WIFI\_STATE, CHANGE\_WIFI\_MULTICAST\_STATE (agar discovery use ho)
- CAMERA (QR scan ke liye)
- POST\_NOTIFICATIONS (Android 13+)
- FOREGROUND\_SERVICE, FOREGROUND\_SERVICE\_DATA\_SYNC (Android 14+), WAKE\_LOCK
- Service: HostForegroundService, foregroundServiceType = dataSync
- usesCleartextTraffic = true ya network\_security\_config se local network allow (ws:// ke liye zaroori)
- Video chunne ke liye Storage Access Framework (ACTION\_OPEN\_DOCUMENT, video/\*). Alag storage permission nahi.

## 5. Network protocol

Ek hi WebSocket connection (port 8080). Text frames = JSON control messages. Binary frames = chunk data.

### 5.1 Text messages (JSON)

Har message mein type field hota hai.

| type | Kaun bhejta hai | Fields | Matlab |
| --- | --- | --- | --- |
| join | Participant | name | Room join karna |
| welcome | Host | userId, roomName, participants | Join approve |
| reject | Host | reason (full / locked) | Join fail |
| participants | Host | list of (userId, name) | Participant list update |
| chunk\_meta | Host | chunkNo, size, sha256, durMs, startMs, totalFrames | Chunk shuru, iske baad binary frames |
| progress | Participant | chunkNo, percent | Download progress |
| ready | Participant | chunkNo | Chunk verify ho gaya |
| waiting | Host | names (list of slow users with percent) | Buffering banner |
| play\_at | Host | chunkNo, atHostMs, posMs | Is host-time par play |
| pause | Host | posMs | Sab ruko |
| resume | Host | atHostMs, posMs | Is time par chalo |
| sync | Host | posMs, hostMs | Har 5 sec mein, drift correction |
| time\_req | Participant | t0 | Clock sync |
| time\_res | Host | t0, hostMs | Clock sync jawab |
| chunk\_done | Participant | chunkNo | Maine chalake delete kar diya |
| chat | Sab (host relay karta hai) | from, text, ts | Chat |
| reaction | Sab (host relay karta hai) | from, emoji | Reaction |
| kick / mute | Host | userId | Host controls (stretch) |
| ended | Host | - | Movie khatam ya room band |

### 5.2 Binary frame format

Har binary frame: pehle 8 bytes header, phir payload.

- bytes 0-3: chunkNo (int32, big endian)
- bytes 4-7: frameIndex (int32, big endian)
- bytes 8 se: payload (max 256 KB)

Receiver chunkNo + frameIndex se jodta hai. totalFrames = ceil(size / 262144). Last frame chhoti ho sakti hai.

### 5.3 Rules

- Host kisi participant ko chunk bhejte waqt control messages (pause jaise) frames ke beech mein bhi bhej sakta hai. Isliye chunk ko 256 KB frames mein bhejo aur har frame ke beech coroutine yield karo.
- Client ke messages host par validate hon: sirf host play\_at/pause/resume/chunk\_meta bhej sakta hai. Participant ke aise messages ignore karo.
- Host sab chat/reaction messages ko sab participants ko relay karta hai.
- Room mein max 6 log (host + 5). 7th ko reject(full).

## 6. Chunker (media/Chunker.kt)

**Input:** video content Uri, chunk target duration (first chunk 10 sec, baaki 30 sec; demo ke liye sab 10 sec rakh sakte ho, config mein ek constant). **Output:** Flow of ChunkInfo(chunkNo, file, startMs, durMs, size, sha256).

**Algorithm (MediaExtractor + MediaMuxer):**

1. MediaExtractor ko content Uri se open karo (setDataSource(context, uri, null)).
2. Tracks dekho. Video track (video/avc) aur audio track (audio/mp4a-latm) chuno. Agar codec ye nahi hai to UnsupportedFormat error do aur UI par message dikhao: Sirf H.264/AAC mp4 supported hai.
3. Dono tracks select karo. Muxer ke liye addTrack karo.
4. Chunk ki target end time nikalo. Video track par seekTo(target, SEEK\_TO\_NEXT\_SYNC) se agla keyframe dhoondho. Wahi chunk ka end hai. Isliye chunk ki asli duration target se thodi alag ho sakti hai.
5. Samples padho (readSampleData, sampleTime, sampleFlags) aur muxer.writeSampleData se likho. Timestamps ko chunk ke start se rebase karo (chunk ka pehla sample time 0 ho).
6. Audio aur video dono tracks ke samples chunk ke time window ke andar hi likho, nahi to lip-sync bigadega.
7. Muxer stop karo. File cacheDir/host\_chunks/chunk\_N.mp4 mein ho.
8. SHA-256 nikalo, size nikalo, ChunkInfo emit karo.

**Pipeline rule:** chunker sirf 2 chunks aage rahe (bounded channel capacity 2). Jab ek chunk sab ne chala liya (sabke chunk\_done aa gaye), tab uski host file delete ho aur agla chunk bane.

**Edge cases:**

- Video <10 sec: ek hi chunk
- Variable frame rate: koi dikkat nahi, timestamps rebase se theek
- Seek (stretch goal): naye position se chunker restart
- Chunker IO dispatcher par chale, main thread par nahi

**(DECISION)** Agar Phase 1 test mein chunks ke beech gap/click dikhe, to FragmentedMp4Muxer (Media3) try karo. Pehle MediaMuxer se shuru karo.

## 7. Receiver (media/ChunkReceiver.kt)

1. chunk\_meta aane par ek .part file kholo (cacheDir/chunks/chunk\_N.part), expected size aur sha256 yaad rakho.
2. Har binary frame header se frameIndex nikalo aur payload ko file mein sahi offset par likho (frameIndex \* 262144).
3. Progress har 5% par progress message bhejo.
4. Saare frames aane par: file size check, phir SHA-256. Match ho to rename chunk\_N.mp4, ready(N) bhejo, chunk ko ChunkPlayer ko do.
5. Mismatch ho to file delete, host ko resend maangne ke liye ek resend(chunkNo) message bhejo (max 2 baar), phir error dikhao.
6. Chunk N chalne ke baad chunk N-1 ki file delete aur chunk\_done bhejo.
7. App band hone, room chhodne ya ended message par cacheDir/chunks poori delete. App start par bhi purani files saaf karo.
8. Files ke saath .nomedia rakho, taaki gallery mein na dikhein.

## 8. Clock sync (sync/ClockSync.kt)

Host ka SystemClock.elapsedRealtime() (ya System.currentTimeMillis) master hai.

1. Participant join ke baad 8 baar time\_req bhejta hai (200 ms gap par), t0 = local time.
2. Host time\_res(t0, hostMs) bhejta hai.
3. Participant t1 = local time par jawab pata hai. rtt = t1 - t0. offset = hostMs + rtt/2 - t1.
4. Sabse kam rtt wale sample ka offset rakho.
5. Har 60 sec mein dobara.
6. toLocal(hostMs) = hostMs - offset.

Host ke liye offset = 0.

## 9. Sync engine (sync/SyncEngine.kt)

### 9.1 Participant state machine

| State | Matlab | Agla |
| --- | --- | --- |
| IDLE | Lobby | LOADING (chunk\_meta aaya) |
| LOADING | Chunk download ho raha hai | READY\_WAIT |
| READY\_WAIT | ready bhej diya | PLAYING (play\_at par) |
| PLAYING | Chunk chal raha hai | BUFFERING / PAUSED |
| BUFFERING | Agla chunk nahi aaya | PLAYING |
| PAUSED | Host ne roka | PLAYING (resume par) |
| ENDED | Movie khatam | IDLE |

### 9.2 Host logic

1. Host chunk 1 banata hai aur sab ko bhejta hai.
2. Sab participants ke ready(1) ka intezar. Beech mein waiting(names) broadcast karo (har 1 sec).
3. Sab ready ho jayein (ya host Start anyway dabaye): play\_at(chunkNo=1, atHostMs = now + 2000, posMs = 0) broadcast.
4. Host khud bhi atHostMs par play karta hai.
5. Chunk 1 chalte waqt chunk 2 ban kar jaata hai. Agar chunk N khatam hone se 3 sec pehle tak sab ke paas N+1 nahi hai, to host pause karke waiting dikhata hai, aur sab ready hone par resume(atHostMs = now + 1500).
6. Har 5 sec sync(posMs, hostMs) broadcast.

### 9.3 Participant play\_at handling

1. localAt = ClockSync.toLocal(atHostMs).
2. ExoPlayer pehle se prepare kar ke pause rakho (STATE\_READY).
3. delay = localAt - now. Agar delay > 0: coroutine delay(delay - 20), phir aakhri 20 ms busy-wait, phir player.play(). Agar delay <= 0 (late): player.seekTo(-delay ms) karke play.

### 9.4 Drift correction

sync message par expected = posMs + (toHostTime(now) - hostMs). Fark = player.position - expected.

- |fark| < 100 ms: kuch nahi
- 100 se 500 ms: playbackParameters speed 0.97 (agar aage hai) ya 1.03 (agar peeche), jab tak fark < 40 ms
- 500 ms se zyada: seekTo(expected), speed 1.0

### 9.5 Pause aur resume

pause(posMs): sab turant pause karke seekTo(posMs). resume(atHostMs, posMs): play\_at jaisa.

## 10. Player (media/ChunkPlayer.kt)

- Ek ExoPlayer instance, playlist mode (setMediaItems, addMediaItem).
- Chunk N ready hote hi addMediaItem(file Uri).
- prepare() jaldi karo, taaki play ke waqt turant chalna ho. playWhenReady = false jab tak play\_at na aaye.
- Global position = chunk.startMs + currentPosition (UI ke progress bar ke liye).
- Participant ke liye controls band (seek bar sirf dikhta hai, drag nahi hota). Host ke liye play/pause button.
- Chunk change hone par (onMediaItemTransition) SyncEngine ko batao, purani chunk ki file delete.
- Gap check: agar 2 chunks ke beech 150 ms se zyada ruk raha hai, log karo (testing ke liye).

## 11. UI aur screens

### 11.1 Design system

- Theme: dark. Background #0F1115, surface #1A1D24, card #232733.
- Primary: #7C5CFF (purple), secondary: #26D0CE (teal), error: #FF5C7A, success: #3DDC97, text #F2F4F8, muted #8A90A2.
- Font: system default (Roboto), headings SemiBold, body Regular.
- Corners 16 dp (cards), 12 dp (buttons), spacing 8 dp grid.
- Buttons: Primary filled (purple), Secondary outlined. Min touch target 48 dp.
- Icons: Material Icons Extended.
- Landscape: Room screen landscape mein full-screen video ke saath chale. Baaki screens portrait.

### 11.2 Navigation

Home -> HostSetup -> Lobby(host) -> Room Home -> Join (QR/IP) -> Lobby(participant) -> Room Room se back dabane par Leave confirm dialog.

### 11.3 Screen 1: Home

```
+---------------------------+
|                           |
|        (logo icon)        |
|       Watch Party         |
|  Dosto ke saath ek hi     |
|  time par dekho           |
|                           |
|  [ Your name        ]     |
|                           |
|  [  Create Party      ]   |
|  [  Join Party        ]   |
|                           |
|  Same WiFi par chalta hai |
+---------------------------+
```

- Name field zaroori (min 2 chars). Dono buttons name ke baad enabled.
- Footer ek hint: Sabhi phones same WiFi ya host ke hotspot par hon.

### 11.4 Screen 2: Host Setup

```
+---------------------------+
| <  Create Party           |
|                           |
|  [ Select video       ]   |
|  movie.mp4  1080p  3.6 GB |
|  H.264 / AAC   OK         |
|                           |
|  Chunk length  ( 10s | 30s)|
|                           |
|  Tips:                    |
|  - Charger par rakhein    |
|  - Screen on rakhein      |
|                           |
|  [ Create room        ]   |
+---------------------------+
```

- Select video: SAF picker. Chunne ke baad codec check (MediaExtractor). Fail ho to red message: Format supported nahi (H.264/AAC mp4 chahiye).
- Create room: HostServer start, foreground service start, Lobby khulta hai.

### 11.5 Screen 3: Lobby (host)

```
+---------------------------+
| <  Lobby          Room 4F2|
|                           |
|      +-----------+        |
|      |  QR code  |        |
|      +-----------+        |
|   ws://192.168.1.5:8080   |
|                           |
|  Participants (2/6)       |
|  (A) Rahul        Joined  |
|  (A) Priya        Joined  |
|                           |
|  [ Lock room ]            |
|  [ Start party     ]      |
+---------------------------+
```

- QR mein JSON ya URL: ws://IP:8080 + room code.
- Participants real-time list. Start tab enabled jab kam se kam 1 participant ho.

### 11.6 Screen 4: Join

```
+---------------------------+
| <  Join Party             |
|                           |
|  [ Scan QR code       ]   |
|        ya                 |
|  [ Host IP: 192.168.1.5 ] |
|  [ Room code: 4F2     ]   |
|                           |
|  [ Join               ]   |
+---------------------------+
```

- Scan QR: camera permission, ML Kit scan, fields apne aap bhar jaayein.
- Join fail hone par error (Room full, Room locked, Host nahi mila).
- Participant Lobby: Host ke start karne ka intezar, participants ki list.

### 11.7 Screen 5: Room (main)

**Portrait:**

```
+---------------------------+
| Room 4F2      (battery)   |
|---------------------------|
|                           |
|     [  VIDEO PLAYER  ]    |
|     (16:9, ExoPlayer)     |
|                           |
| 00:12:30 ----o------ 2:01 |
| [ Pause ]  (host only)    |
|---------------------------|
| (A)Rahul (A)Priya (A)Amit |  <- participant strip
|---------------------------|
|  Rahul: kya scene hai     |
|  Priya: haha              |
|---------------------------|
| [ message...         ] [>]|
| (heart)(fire)(clap)(laugh)|
+---------------------------+
```

**Status banner** (video ke upar overlay):

- Chunk 3 aa raha hai... 62% (download)
- Buffering: Rahul ka chunk aa raha hai (62%)
- Host ne pause kiya
- Sync ho raha hai...

**Reactions:** emoji dabate hi video ke upar floating animation (neeche se upar), aur sabke phone par dikhe. Rate limit 3 per second.

**Host ke extra:** top bar mein menu (Participants, Lock room, End party). Participant ko sirf Leave.

**Landscape:** video full-screen, chat ek overlay panel (swipe se), reactions floating.

### 11.8 Screen 6: Ended

Movie khatam ya host ne end kiya: Party khatam hui. Button: Back to Home. Cache delete ho chuka ho.

### 11.9 Common UI states

| Situation | UI |
| --- | --- |
| Host nahi mila | Error snackbar + Retry |
| Connection toot gaya | Reconnecting... banner (prototype mein: Leave aur dobara join) |
| Chunk SHA mismatch | Retry ho raha hai, 2 baar ke baad error |
| Storage kam | Pehle hi chunk se pehle check, error |

## 12. End-to-end workflow

1. Host: Home -> naam -> Create Party -> video chuno -> Create room.
2. HostServer 8080 par start. Lobby mein QR dikhta hai.
3. Participants: Home -> naam -> Join Party -> QR scan -> join message -> welcome.
4. ClockSync chalta hai (8 sample).
5. Host Start dabata hai: Chunker chunk 1 banata hai.
6. Host chunk\_meta + binary frames sab ko bhejta hai. Participants progress bhejte hain.
7. Sab ready -> host play\_at(1) bhejta hai (2 sec ka countdown, UI par 3-2-1).
8. Sab ek saath play. Chunk 2 background mein jaata hai.
9. Chunk 1 khatam, chunk 2 chalta hai (gapless). Purani files delete, chunk\_done.
10. Beech mein host pause/resume kar sakta hai, chat aur reactions chalte rehte hain.
11. Last chunk ke baad ended, cleanup, Ended screen.

## 13. Local network ke tips

- Host ka IP: WifiManager ya NetworkInterface se nikalo (192.168.x.x ya hotspot ka 192.168.43.1).
- Agar router mein AP/Client Isolation on hai to phones judte nahi. Fix: host ka mobile hotspot.
- WebSocket server ke liye host phone ko screen on aur foreground service chahiye.
- Chunk transfer ki speed (WiFi 5): \~10 MB / 2 sec. Hotspot par thodi kam.

## 14. Build order (agent ke liye)

Har step ke baad build, test, aur CHANGELOG update.

| Step | Kaam | Acceptance check |
| --- | --- | --- |
| 1 | Project setup, theme, navigation, Home screen | App khule, Home dikhe, naam validate ho |
| 2 | Chunker + ChunkPlayer. Ek hi phone par local video ke 3 chunks banao aur chalao | 3 chunks bina bade gap ke chalein (gap <150 ms), audio-video sync rahe |
| 3 | Protocol.kt, HostServer, ClientConnection, Lobby, QR create/scan, Join | 2 phones judein, participants list dono par dikhe |
| 4 | ChunkSender + ChunkReceiver, SHA-256 verify, progress | Chunk dusre phone par pahunche, SHA match, file chalne ke baad delete |
| 5 | ClockSync + SyncEngine: ready, play\_at, pause, resume, waiting banner | 2 phones ek saath start hon (fark <500 ms, aankhon se theek dikhe) |
| 6 | Pipeline: chunk 1, 2, 3 lagatar, lead buffer, drift correction | 5 minute ki video bina atke poori chale |
| 7 | Chat, reactions, host controls (lock/end), Room UI polish, landscape | 4 phones par sab feature chalein |
| 8 | HostForegroundService, battery/temperature warning, error states, cleanup | Screen off hone par bhi host chalta rahe, app band hone par cache delete |
| 9 (stretch) | Seek: host seek bar se naye position se chunker restart | Seek ke baad sab ek saath resume |

## 15. Test plan aur demo script

**Test:** 1 host + 3 participants. Normal, WiFi band (ek phone), app kill (ek participant), host ka pause/resume 10 baar, 10 minute ki video poori.

**Demo script (5 minute):**

1. Host Create Party, QR dikhata hai, 3 phones scan karke judte hain.
2. Host video chunta hai aur Start dabata hai. Sab ke progress bars dikhte hain.
3. Countdown 3-2-1, **sab phones par ek saath play**.
4. Pause/resume sab par ek saath.
5. Ek phone ka WiFi band: Buffering Rahul... banner.
6. Chat aur reactions.
7. Batao ki asli product mein camera/voice (VideoHub), internet par chalna, 6 log aur reconnect judenge.

## 16. Known risks

| Risk | Bachav |
| --- | --- |
| Chunks ke beech gap | Chunk ke prepare pehle se, ya FragmentedMp4Muxer |
| Sync 500 ms se zyada | Drift correction, prepare pehle se, play ke waqt busy-wait |
| Host ka phone garam | Chunk ahead 2 rakho, charger |
| AP isolation | Mobile hotspot |
| Android background kill | Foreground service |
| Video codec unsupported | Format check, clear error |

## 17. Agent ko dene wala prompt (copy-paste)

```
You are an expert Android (Kotlin, Jetpack Compose) engineer.
Build the Watch Party prototype exactly as described in the attached spec.
Work step by step following Section 14. After each step:
1) build the app, 2) fix errors, 3) check the acceptance criteria,
4) add an entry to CHANGELOG.md, then continue.
Do not invent library APIs; read official docs when unsure.
If the spec conflicts with a device limitation, stop and explain.
Start with Step 1 and 2 first. Show me results before Step 3.
```
