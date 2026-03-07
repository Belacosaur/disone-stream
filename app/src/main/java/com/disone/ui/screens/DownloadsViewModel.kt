package com.disone.ui.screens

import androidx.lifecycle.ViewModel
import com.disone.core.torrent.TorrentInfo
import com.disone.core.torrent.TorrentSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val torrentSession: TorrentSession
) : ViewModel() {

    val torrents: StateFlow<Map<String, TorrentInfo>> = torrentSession.torrents

    fun removeTorrent(infoHash: String) {
        torrentSession.removeTorrent(infoHash)
    }
}
