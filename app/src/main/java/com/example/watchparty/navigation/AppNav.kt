package com.example.watchparty.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.watchparty.sync.PartySyncState
import com.example.watchparty.ui.home.HomeScreen
import com.example.watchparty.ui.home.HomeViewModel
import com.example.watchparty.ui.host.HostSetupScreen
import com.example.watchparty.ui.host.LobbyScreen
import com.example.watchparty.ui.join.JoinScreen
import com.example.watchparty.ui.join.QrScanScreen
import com.example.watchparty.ui.room.EndedScreen
import com.example.watchparty.ui.room.RoomScreen
import com.example.watchparty.ui.room.RoomViewModel

object WatchPartyRoutes {
    const val ROUTE_HOME = "home"
    const val ROUTE_HOST_SETUP = "host_setup"
    const val ROUTE_LOBBY = "lobby"
    const val ROUTE_JOIN = "join"
    const val ROUTE_QR_SCAN = "qr_scan"
    const val ROUTE_ROOM = "room"
    const val ROUTE_ENDED = "ended"
}

@Composable
fun AppNav(
    homeViewModel: HomeViewModel = viewModel(),
    roomViewModel: RoomViewModel = viewModel()
) {
    val navController = rememberNavController()

    val userName by homeViewModel.userName.collectAsStateWithLifecycle()
    val localIp by roomViewModel.localIp.collectAsStateWithLifecycle()
    val hasEnoughStorage by roomViewModel.hasEnoughStorage.collectAsStateWithLifecycle()
    val deviceHealth by roomViewModel.deviceHealth.collectAsStateWithLifecycle()
    val videoProbe by roomViewModel.videoProbe.collectAsStateWithLifecycle()
    val isGeneratingSample by roomViewModel.isGeneratingSample.collectAsStateWithLifecycle()
    val selectedChunkSec by roomViewModel.selectedChunkSec.collectAsStateWithLifecycle()
    val hostRoomPassword by roomViewModel.hostRoomPassword.collectAsStateWithLifecycle()
    val isHost by roomViewModel.isHost.collectAsStateWithLifecycle()
    val roomCode by roomViewModel.roomCode.collectAsStateWithLifecycle()
    val joinHostIp by roomViewModel.joinHostIp.collectAsStateWithLifecycle()
    val joinRoomCode by roomViewModel.joinRoomCode.collectAsStateWithLifecycle()
    val joinRoomPassword by roomViewModel.joinRoomPassword.collectAsStateWithLifecycle()
    val joinError by roomViewModel.joinError.collectAsStateWithLifecycle()
    val isConnecting by roomViewModel.isConnecting.collectAsStateWithLifecycle()
    val participants by roomViewModel.participants.collectAsStateWithLifecycle()
    val isRoomLocked by roomViewModel.isRoomLocked.collectAsStateWithLifecycle()
    val chatMessages by roomViewModel.chatMessagesFlow.collectAsStateWithLifecycle()
    val floatingReactions by roomViewModel.floatingReactions.collectAsStateWithLifecycle()

    val discoveredRooms by roomViewModel.roomDiscovery.discoveredRooms.collectAsStateWithLifecycle()
    val isScanningRooms by roomViewModel.roomDiscovery.isScanning.collectAsStateWithLifecycle()

    val isMicOn by roomViewModel.voiceEngine.isMicOn.collectAsStateWithLifecycle()
    val isLocalSpeaking by roomViewModel.voiceEngine.isLocalSpeaking.collectAsStateWithLifecycle()
    val isSpeakerOn by roomViewModel.voiceEngine.isSpeakerOn.collectAsStateWithLifecycle()
    val activeRemoteSpeaker by roomViewModel.voiceEngine.activeRemoteSpeaker.collectAsStateWithLifecycle()
    val videoVolume by roomViewModel.videoVolume.collectAsStateWithLifecycle()
    val voiceVolume by roomViewModel.voiceEngine.voiceVolume.collectAsStateWithLifecycle()
    val isAutoDuckEnabled by roomViewModel.isAutoDuckEnabled.collectAsStateWithLifecycle()
    val playbackSpeed by roomViewModel.playbackSpeed.collectAsStateWithLifecycle()

    NavHost(
        navController = navController,
        startDestination = WatchPartyRoutes.ROUTE_HOME
    ) {
        composable(WatchPartyRoutes.ROUTE_HOME) {
            HomeScreen(
                userName = userName,
                onUserNameChange = homeViewModel::updateUserName,
                onCreatePartyClick = {
                    navController.navigate(WatchPartyRoutes.ROUTE_HOST_SETUP)
                },
                onJoinPartyClick = {
                    navController.navigate(WatchPartyRoutes.ROUTE_JOIN)
                },
                localIp = localIp
            )
        }

        composable(WatchPartyRoutes.ROUTE_HOST_SETUP) {
            HostSetupScreen(
                probeResult = videoProbe,
                isGeneratingSample = isGeneratingSample,
                selectedChunkSec = selectedChunkSec,
                roomPassword = hostRoomPassword,
                hasEnoughStorage = hasEnoughStorage,
                onVideoPicked = roomViewModel::selectVideoUri,
                onGenerateSampleVideo = roomViewModel::generateBuiltInSampleVideo,
                onSelectChunkSec = roomViewModel::setChunkLengthSec,
                onRoomPasswordChange = roomViewModel::updateHostRoomPassword,
                onCreateRoomClick = {
                    roomViewModel.createHostRoom(userName) {
                        navController.navigate(WatchPartyRoutes.ROUTE_LOBBY)
                    }
                },
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }

        composable(WatchPartyRoutes.ROUTE_LOBBY) {
            LobbyScreen(
                isHost = isHost,
                roomCode = roomCode,
                roomPassword = if (isHost) hostRoomPassword else joinRoomPassword,
                hostIp = if (isHost) localIp else joinHostIp,
                participants = participants,
                isRoomLocked = isRoomLocked,
                onToggleLock = roomViewModel::toggleRoomLock,
                onAddSimulatedParticipant = roomViewModel::addSimulatedFriendInLobby,
                onStartPartyClick = {
                    roomViewModel.startHostPartyPlayback {
                        navController.navigate(WatchPartyRoutes.ROUTE_ROOM) {
                            popUpTo(WatchPartyRoutes.ROUTE_HOME)
                        }
                    }
                },
                onLeaveLobbyClick = {
                    roomViewModel.cleanupActiveSession()
                    navController.popBackStack(WatchPartyRoutes.ROUTE_HOME, inclusive = false)
                }
            )
        }

        composable(WatchPartyRoutes.ROUTE_JOIN) {
            DisposableEffect(Unit) {
                roomViewModel.startScanningForRooms()
                onDispose {
                    roomViewModel.stopScanningForRooms()
                }
            }

            JoinScreen(
                hostIp = joinHostIp,
                roomCode = joinRoomCode,
                roomPassword = joinRoomPassword,
                discoveredRooms = discoveredRooms,
                isScanningRooms = isScanningRooms,
                isConnecting = isConnecting,
                errorMessage = joinError,
                onHostIpChange = roomViewModel::updateJoinHostIp,
                onRoomCodeChange = roomViewModel::updateJoinRoomCode,
                onRoomPasswordChange = roomViewModel::updateJoinRoomPassword,
                onSelectDiscoveredRoom = { room, pwd ->
                    roomViewModel.selectDiscoveredRoomAndJoin(
                        room = room,
                        password = pwd,
                        userName = userName,
                        onJoinedLobby = {
                            navController.navigate(WatchPartyRoutes.ROUTE_LOBBY)
                        },
                        onRoomStarted = {
                            navController.navigate(WatchPartyRoutes.ROUTE_ROOM) {
                                popUpTo(WatchPartyRoutes.ROUTE_HOME)
                            }
                        }
                    )
                },
                onOpenQrScanner = {
                    navController.navigate(WatchPartyRoutes.ROUTE_QR_SCAN)
                },
                onJoinClick = {
                    roomViewModel.joinPartyAsParticipant(
                        userName = userName,
                        onJoinedLobby = {
                            navController.navigate(WatchPartyRoutes.ROUTE_LOBBY)
                        },
                        onRoomStarted = {
                            navController.navigate(WatchPartyRoutes.ROUTE_ROOM) {
                                popUpTo(WatchPartyRoutes.ROUTE_HOME)
                            }
                        }
                    )
                },
                onBackClick = {
                    roomViewModel.cleanupActiveSession()
                    navController.popBackStack()
                }
            )
        }

        composable(WatchPartyRoutes.ROUTE_QR_SCAN) {
            QrScanScreen(
                onQrDetected = { payload ->
                    roomViewModel.onQrScanned(payload)
                    navController.popBackStack()
                },
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }

        composable(WatchPartyRoutes.ROUTE_ROOM) {
            val engine = roomViewModel.syncEngine
            if (engine != null) {
                val syncState by engine.syncState.collectAsStateWithLifecycle()
                val isPlaying by engine.chunkPlayer.isPlaying.collectAsStateWithLifecycle()
                val globalPosMs by engine.chunkPlayer.globalPositionMs.collectAsStateWithLifecycle()
                val queuedDurationMs by engine.chunkPlayer.totalQueuedDurationMs.collectAsStateWithLifecycle()
                val statusBanner by engine.statusBanner.collectAsStateWithLifecycle()
                val countdownNumber by engine.countdownNumber.collectAsStateWithLifecycle()
                val canStartAnyway by engine.canStartAnyway.collectAsStateWithLifecycle()

                LaunchedEffect(syncState) {
                    if (syncState == PartySyncState.ENDED) {
                        navController.navigate(WatchPartyRoutes.ROUTE_ENDED) {
                            popUpTo(WatchPartyRoutes.ROUTE_HOME)
                        }
                    }
                }

                val effectiveTotalDuration = maxOf(
                    videoProbe?.durationMs ?: 0L,
                    queuedDurationMs
                )

                RoomScreen(
                    isHost = isHost,
                    roomCode = roomCode,
                    roomPassword = if (isHost) hostRoomPassword else joinRoomPassword,
                    hostIp = if (isHost) localIp else joinHostIp,
                    exoPlayer = engine.chunkPlayer.exoPlayer,
                    isPlaying = isPlaying,
                    globalPositionMs = globalPosMs,
                    totalDurationMs = effectiveTotalDuration,
                    statusBanner = statusBanner,
                    countdownNumber = countdownNumber,
                    canStartAnyway = canStartAnyway,
                    isRoomLocked = isRoomLocked,
                    isMicOn = isMicOn,
                    isLocalSpeaking = isLocalSpeaking,
                    isSpeakerOn = isSpeakerOn,
                    activeRemoteSpeaker = activeRemoteSpeaker,
                    videoVolume = videoVolume,
                    voiceVolume = voiceVolume,
                    isAutoDuckEnabled = isAutoDuckEnabled,
                    playbackSpeed = playbackSpeed,
                    deviceHealth = deviceHealth,
                    participants = participants,
                    chatMessages = chatMessages,
                    floatingReactions = floatingReactions,
                    onPlayPauseToggle = roomViewModel::toggleHostPlayPause,
                    onStartAnywayClick = roomViewModel::triggerStartAnyway,
                    onHostSeekTo = roomViewModel::seekHostTo,
                    onToggleMic = roomViewModel::toggleMic,
                    onToggleSpeaker = roomViewModel::toggleSpeaker,
                    onVideoVolumeChange = roomViewModel::setVideoVolume,
                    onVoiceVolumeChange = roomViewModel::setVoiceVolume,
                    onToggleAutoDuck = roomViewModel::toggleAutoDuck,
                    onPlaybackSpeedChange = roomViewModel::setPlaybackSpeed,
                    onAddSimulatedParticipant = roomViewModel::addSimulatedFriendInLobby,
                    onSendChat = { text, replyTo ->
                        roomViewModel.sendChatMessage(userName, text, replyTo)
                    },
                    onSendReaction = { emoji -> roomViewModel.sendReaction(userName, emoji) },
                    onSendSoundEffect = { sfx -> roomViewModel.sendSoundEffect(userName, sfx) },
                    onReactionFinished = roomViewModel::removeFinishedReaction,
                    onToggleLock = roomViewModel::toggleRoomLock,
                    onKickParticipant = roomViewModel::kickParticipant,
                    onMuteParticipant = roomViewModel::toggleMuteParticipant,
                    onLeaveOrEndParty = {
                        roomViewModel.cleanupActiveSession()
                        navController.navigate(WatchPartyRoutes.ROUTE_ENDED) {
                            popUpTo(WatchPartyRoutes.ROUTE_HOME)
                        }
                    }
                )
            }
        }

        composable(WatchPartyRoutes.ROUTE_ENDED) {
            EndedScreen(
                onBackToHomeClick = {
                    roomViewModel.cleanupActiveSession()
                    navController.popBackStack(WatchPartyRoutes.ROUTE_HOME, inclusive = false)
                }
            )
        }
    }
}
