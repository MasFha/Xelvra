package com.mdevz.xelvra.core.xray

sealed interface DelayQualityState {

    data object NotAvailable : DelayQualityState

    data object Measuring : DelayQualityState

    data class Success(
        val milliseconds: Long,
        val measuredAt: Long
    ) : DelayQualityState

    data class Timeout(
        val measuredAt: Long
    ) : DelayQualityState

    data class Failed(
        val measuredAt: Long
    ) : DelayQualityState
}
