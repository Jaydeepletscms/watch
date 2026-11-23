package com.example.watchparty.media

import android.content.Context
import java.io.File

data class ChunkInfo(
    val chunkNo: Int,
    val file: File,
    val startMs: Long,
    val durMs: Long,
    val size: Long,
    val sha256: String
) {
    val totalFrames: Int
        get() = ((size + 262_144L - 1L) / 262_144L).toInt().coerceAtLeast(1)
}

class ChunkStore(context: Context) {
    private val cacheRoot: File = context.cacheDir
    val hostChunksDir: File = File(cacheRoot, "host_chunks")
    val participantChunksDir: File = File(cacheRoot, "chunks")

    init {
        ensureDirectories()
    }

    fun ensureDirectories() {
        if (!hostChunksDir.exists()) hostChunksDir.mkdirs()
        if (!participantChunksDir.exists()) participantChunksDir.mkdirs()
        // Section 7.8: Keep .nomedia so chunks never appear in Gallery
        File(hostChunksDir, ".nomedia").apply { if (!exists()) createNewFile() }
        File(participantChunksDir, ".nomedia").apply { if (!exists()) createNewFile() }
    }

    fun getHostChunkFile(chunkNo: Int): File {
        ensureDirectories()
        return File(hostChunksDir, "chunk_$chunkNo.mp4")
    }

    fun getParticipantPartFile(chunkNo: Int): File {
        ensureDirectories()
        return File(participantChunksDir, "chunk_$chunkNo.part")
    }

    fun getParticipantChunkFile(chunkNo: Int): File {
        ensureDirectories()
        return File(participantChunksDir, "chunk_$chunkNo.mp4")
    }

    fun deleteHostChunk(chunkNo: Int) {
        File(hostChunksDir, "chunk_$chunkNo.mp4").takeIf { it.exists() }?.delete()
    }

    fun deleteParticipantChunk(chunkNo: Int) {
        File(participantChunksDir, "chunk_$chunkNo.part").takeIf { it.exists() }?.delete()
        File(participantChunksDir, "chunk_$chunkNo.mp4").takeIf { it.exists() }?.delete()
    }

    fun clearAll() {
        hostChunksDir.listFiles()?.forEach { file ->
            if (file.name != ".nomedia") file.delete()
        }
        participantChunksDir.listFiles()?.forEach { file ->
            if (file.name != ".nomedia") file.delete()
        }
    }
}
