package com.mdevz.xelvra.core.crash

data class CrashReport(
    val reportId: String,
    val timestamp: Long,
    val exceptionType: String,
    val exceptionMessage: String?,
    val className: String?,
    val methodName: String?,
    val fileName: String?,
    val lineNumber: Int?,
    val fullReport: String
)
