package com.disone.core.torrent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stub TorrentSession - P2P mode not supported without jlibtorrent.
 * Use HOSTED_PROGRESSIVE or HOSTED_HLS modes (stream-server handles torrents).
 */
@Singleton
class TorrentSession @Inject constructor() {

    private val _torrents = MutableStateFlow<Map<String, TorrentInfo>>(emptyMap())
    val torrents: StateFlow<Map<String, TorrentInfo>> = _torrents.asStateFlow()

    fun initialize(downloadDir: File) {
        // No-op
    }

    fun addTorrent(magnet: String, fileIndex: Int = 0): String? = null

    fun getTorrentInfo(infoHash: String): TorrentInfo? = _torrents.value[infoHash]

    fun updateTorrentStatus(infoHash: String, info: TorrentInfo) {
        _torrents.value = _torrents.value + (infoHash to info)
    }

    fun removeTorrent(infoHash: String) {
        _torrents.value = _torrents.value - infoHash
    }

    fun getFilePath(infoHash: String, fileIndex: Int): String? = null

    fun getProgress(infoHash: String): TorrentProgress? = null

    fun release() {
        _torrents.value = emptyMap()
    }
}

data class TorrentInfo(
    val infoHash: String,
    val name: String,
    val files: List<TorrentFileInfo>,
    val magnet: String
)

data class TorrentFileInfo(
    val index: Int,
    val path: String,
    val size: Long
)

data class TorrentProgress(
    val progressPercent: Double,
    val downloadSpeed: Long,
    val numPeers: Int,
    val numSeeds: Int,
    val totalBytes: Long,
    val progressBytes: Long
)
