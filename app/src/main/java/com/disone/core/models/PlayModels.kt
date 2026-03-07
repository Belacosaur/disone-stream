package com.disone.core.models

import com.google.gson.annotations.SerializedName

data class NonceResponse(
    val nonce: String
)

data class AuthVerifyRequest(
    val wallet: String,
    val signature: String,
    val nonce: String
)

data class AuthVerifyResponse(
    val token: String,
    val expiresIn: Long,
    val plan: String
)

data class PlayRequest(
    val magnet: String? = null,
    @SerializedName("client_supports_p2p") val clientSupportsP2p: Boolean = true,
    @SerializedName("file_index") val fileIndex: Int? = null,
    @SerializedName("info_hash") val infoHash: String? = null,
    val url: String? = null,
    val stream: PlayStreamRequest? = null
)

data class PlayStreamRequest(
    @SerializedName("info_hash") val infoHash: String? = null,
    val magnet: String? = null,
    val url: String? = null,
    @SerializedName("file_idx") val fileIdx: Int? = null
)

data class PlayResponse(
    val mode: String,
    @SerializedName("stream_url") val streamUrl: String? = null,
    @SerializedName("hls_url") val hlsUrl: String? = null,
    @SerializedName("torrent_metadata") val torrentMetadata: TorrentMetadataDto? = null,
    val plan: String? = null,
    val magnet: String? = null
)

data class TorrentMetadataDto(
    @SerializedName("info_hash") val infoHash: String,
    val name: String?,
    val files: List<TorrentFileDto>
)

data class TorrentFileDto(
    val index: Int,
    val path: String,
    val size: Long
)
