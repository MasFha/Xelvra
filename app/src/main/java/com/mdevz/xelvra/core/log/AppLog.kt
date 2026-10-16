package com.mdevz.xelvra.core.log

import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArraySet

object AppLog {

    enum class Category {
        APP,
        VPN,
        XRAY,
        TRAFFIC
    }

    enum class Level {
        INFO,
        WARNING,
        ERROR
    }

    data class Entry(
        val timestamp: Long,
        val category: Category,
        val level: Level,
        val message: String
    ) {
        fun format(): String {
            val time = formatTime()

            return "$time  [${category.name}] [${level.name}]  $message"
        }

        fun formatDisplay(): String {
            val time = formatTime()

            return "$time  $message"
        }

        private fun formatTime(): String =
            SimpleDateFormat(
                "HH:mm",
                Locale.getDefault()
            ).format(Date(timestamp))
    }

    private const val MAX_ENTRIES = 500

    private val lock = Any()

    private val entries =
        ArrayDeque<Entry>()

    private val listeners =
        CopyOnWriteArraySet<() -> Unit>()

    private val mainHandler =
        Handler(Looper.getMainLooper())

    fun write(
        category: Category,
        message: String,
        level: Level = Level.INFO
    ) {
        val clean = message
            .replace('\n', ' ')
            .trim()

        if (clean.isEmpty()) {
            return
        }

        synchronized(lock) {
            entries.addLast(
                Entry(
                    timestamp = System.currentTimeMillis(),
                    category = category,
                    level = level,
                    message = clean
                )
            )

            while (entries.size > MAX_ENTRIES) {
                entries.removeFirst()
            }
        }

        notifyChanged()
    }

    fun app(
        message: String,
        level: Level = Level.INFO
    ) =
        write(Category.APP, message, level)

    fun vpn(
        message: String,
        level: Level = Level.INFO
    ) =
        write(Category.VPN, message, level)

    fun xray(
        message: String,
        level: Level = Level.INFO
    ) =
        write(Category.XRAY, message, level)

    fun traffic(
        message: String,
        level: Level = Level.INFO
    ) =
        write(Category.TRAFFIC, message, level)

    fun snapshot(): List<Entry> =
        synchronized(lock) {
            entries.toList()
        }

    fun clear() {
        synchronized(lock) {
            entries.clear()
        }

        notifyChanged()
    }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyChanged() {
        mainHandler.post {
            listeners.forEach { listener ->
                listener()
            }
        }
    }
}
