package app.librepipes.download

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * Merges a video-only and an audio-only file into a single playable file
 * using the platform MediaMuxer (no FFmpeg required). Supports MP4 and WebM
 * containers. Every extractor is opened and closed locally — no shared state,
 * so concurrent downloads are safe.
 */
object MediaMuxerKit {

    fun mux(videoFile: File, audioFile: File, outputFile: File): Boolean {
        return try {
            val muxer = MediaMuxer(
                outputFile.absolutePath,
                if (isWebm(videoFile)) MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
                else MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )
            val videoTrack = addTrack(muxer, videoFile.absolutePath, "video/")
            val audioTrack = addTrack(muxer, audioFile.absolutePath, "audio/")
            muxer.start()
            copySamples(muxer, videoTrack, videoFile.absolutePath, "video/")
            copySamples(muxer, audioTrack, audioFile.absolutePath, "audio/")
            muxer.stop()
            muxer.release()
            true
        } catch (e: Exception) {
            outputFile.delete()
            false
        }
    }

    private fun isWebm(file: File): Boolean {
        var extractor: MediaExtractor? = null
        return try {
            extractor = MediaExtractor()
            extractor.setDataSource(file.absolutePath)
            val mime = extractor.getTrackFormat(0).getString(MediaFormat.KEY_MIME) ?: ""
            mime.contains("vp9") || mime.contains("vp8") || mime.contains("vp09")
        } catch (e: Exception) {
            false
        } finally {
            extractor?.release()
        }
    }

    private fun addTrack(muxer: MediaMuxer, path: String, mimePrefix: String): Int {
        var extractor: MediaExtractor? = null
        return try {
            extractor = MediaExtractor()
            extractor.setDataSource(path)
            val track = selectTrack(extractor, mimePrefix)
            muxer.addTrack(extractor.getTrackFormat(track))
        } finally {
            extractor?.release()
        }
    }

    private fun copySamples(muxer: MediaMuxer, trackIndex: Int, path: String, mimePrefix: String) {
        var extractor: MediaExtractor? = null
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(path)
            val track = selectTrack(extractor, mimePrefix)
            extractor.selectTrack(track)
            val bufferInfo = MediaCodec.BufferInfo()
            val buffer = ByteBuffer.allocate(1 shl 20)
            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = extractor.sampleFlags
                muxer.writeSampleData(trackIndex, buffer, bufferInfo)
                extractor.advance()
            }
        } finally {
            extractor?.release()
        }
    }

    private fun selectTrack(extractor: MediaExtractor, mimePrefix: String): Int {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith(mimePrefix)) return i
        }
        return 0
    }
}
