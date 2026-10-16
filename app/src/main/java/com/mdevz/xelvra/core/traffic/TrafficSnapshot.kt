package com.mdevz.xelvra.core.traffic

data class TrafficSnapshot(
    val uploadBytesPerSecond: Long = 0L,
    val downloadBytesPerSecond: Long = 0L,
    val sessionUploadBytes: Long = 0L,
    val sessionDownloadBytes: Long = 0L,
    val uploadHistory: List<Long> = emptyList(),
    val downloadHistory: List<Long> = emptyList()
)
