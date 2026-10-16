package com.mdevz.xelvra.network.latency

import android.os.Handler
import android.os.Looper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

sealed interface EndpointLatencyState {

    data object NotTested : EndpointLatencyState

    data object Testing : EndpointLatencyState

    data class Success(
        val milliseconds: Long,
        val measuredAt: Long
    ) : EndpointLatencyState

    data class Timeout(
        val measuredAt: Long
    ) : EndpointLatencyState

    data class Failed(
        val measuredAt: Long
    ) : EndpointLatencyState
}

object EndpointLatencyManager {

    private const val CACHE_TTL_MS =
        5L * 60L * 1000L

    private const val MAX_PARALLEL_TESTS = 4

    private val executor: ExecutorService =
        Executors.newFixedThreadPool(
            MAX_PARALLEL_TESTS
        )

    private val mainHandler =
        Handler(Looper.getMainLooper())

    private val lock = Any()

    private val states =
        mutableMapOf<
            EndpointKey,
            EndpointLatencyState
        >()

    private val running =
        mutableSetOf<EndpointKey>()

    private val listeners =
        mutableSetOf<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        synchronized(lock) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: () -> Unit) {
        synchronized(lock) {
            listeners.remove(listener)
        }
    }

    private fun notifyListeners() {
        val snapshot =
            synchronized(lock) {
                listeners.toList()
            }

        mainHandler.post {
            snapshot.forEach { it() }
        }
    }

    fun getState(
        key: EndpointKey
    ): EndpointLatencyState {
        synchronized(lock) {
            val state =
                states[key]
                    ?: return EndpointLatencyState.NotTested

            if (
                state != EndpointLatencyState.Testing &&
                isExpired(state)
            ) {
                states.remove(key)

                return EndpointLatencyState.NotTested
            }

            return state
        }
    }

    fun test(
        address: String,
        port: Int,
        onStateChanged: () -> Unit
    ) {
        val key = EndpointKey.from(
            address = address,
            port = port
        )

        synchronized(lock) {
            if (!running.add(key)) {
                return
            }

            states[key] =
                EndpointLatencyState.Testing
        }

        mainHandler.post {
            onStateChanged()
            notifyListeners()
        }

        executor.execute {
            val result =
                EndpointLatencyTester.test(
                    address = address,
                    port = port
                )

            val measuredAt =
                System.currentTimeMillis()

            val state =
                when (result) {
                    is EndpointLatencyResult.Success -> {
                        EndpointLatencyState.Success(
                            milliseconds =
                                result.milliseconds,
                            measuredAt =
                                measuredAt
                        )
                    }

                    EndpointLatencyResult.Timeout -> {
                        EndpointLatencyState.Timeout(
                            measuredAt
                        )
                    }

                    EndpointLatencyResult.Failed -> {
                        EndpointLatencyState.Failed(
                            measuredAt
                        )
                    }
                }

            synchronized(lock) {
                states[key] = state
                running.remove(key)
            }

            mainHandler.post {
                onStateChanged()
                notifyListeners()
            }
        }
    }

    fun testAll(
        endpoints: Collection<Pair<String, Int>>,
        onStateChanged: () -> Unit
    ) {
        val unique =
            endpoints
                .associateBy {
                    EndpointKey.from(
                        address = it.first,
                        port = it.second
                    )
                }
                .values

        unique.forEach { endpoint ->
            test(
                address = endpoint.first,
                port = endpoint.second,
                onStateChanged =
                    onStateChanged
            )
        }
    }

    private fun isExpired(
        state: EndpointLatencyState
    ): Boolean {
        val measuredAt =
            when (state) {
                EndpointLatencyState.NotTested,
                EndpointLatencyState.Testing -> {
                    return false
                }

                is EndpointLatencyState.Success -> {
                    state.measuredAt
                }

                is EndpointLatencyState.Timeout -> {
                    state.measuredAt
                }

                is EndpointLatencyState.Failed -> {
                    state.measuredAt
                }
            }

        return System.currentTimeMillis() -
            measuredAt > CACHE_TTL_MS
    }
}
