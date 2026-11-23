package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.watchparty.media.ChunkStore
import com.example.watchparty.net.Protocol
import com.example.watchparty.net.WsMessage
import com.example.watchparty.util.Hash
import com.example.watchparty.util.Qr
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

    @Test
    fun `read Watch Party app_name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Watch Party", appName)
    }

    @Test
    fun `protocol json and binary frame roundtrip`() {
        val meta = WsMessage.ChunkMeta(
            chunkNo = 3,
            size = 1048576L,
            sha256 = "abc123def456",
            durMs = 10000L,
            startMs = 20000L,
            totalFrames = 4
        )
        val jsonStr = Protocol.encodeText(meta)
        val decoded = Protocol.decodeText(jsonStr) as? WsMessage.ChunkMeta
        assertNotNull(decoded)
        assertEquals(3, decoded?.chunkNo)
        assertEquals("abc123def456", decoded?.sha256)

        val payload = ByteArray(1024) { (it % 251).toByte() }
        val encodedFrame = Protocol.encodeBinaryFrame(chunkNo = 2, frameIndex = 5, payload = payload)
        val decodedFrame = Protocol.decodeBinaryFrame(encodedFrame)
        assertNotNull(decodedFrame)
        assertEquals(2, decodedFrame?.chunkNo)
        assertEquals(5, decodedFrame?.frameIndex)
        assertArrayEquals(payload, decodedFrame?.payload)
    }

    @Test
    fun `qr payload and chunk store cleanup`() {
        val qrJson = Qr.encodePayload(ip = "192.168.1.5", port = 8080, roomCode = "4F2")
        val parsed = Qr.parsePayload(qrJson)
        assertNotNull(parsed)
        assertEquals("192.168.1.5", parsed?.ip)
        assertEquals("4F2", parsed?.room)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = ChunkStore(context)
        assertTrue(File(store.participantChunksDir, ".nomedia").exists())
        val dummyChunk = store.getParticipantChunkFile(1)
        dummyChunk.writeBytes("test_video_bytes".toByteArray())
        assertEquals(64, Hash.sha256(dummyChunk).length)
        store.deleteParticipantChunk(1)
        assertTrue(!dummyChunk.exists())
    }
}
