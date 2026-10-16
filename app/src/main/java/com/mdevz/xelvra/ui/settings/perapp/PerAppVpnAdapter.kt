package com.mdevz.xelvra.ui.settings.perapp

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox
import com.mdevz.xelvra.R
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

data class PerAppEntry(
    val packageName: String,
    val label: String
)

class PerAppVpnAdapter(
    private val packageManager: PackageManager,
    private val selectedPackages: MutableSet<String>,
    private val onSelectionChangeRequested:
        (Set<String>) -> Boolean
) : ListAdapter<
    PerAppEntry,
    PerAppVpnAdapter.Holder
>(
    DIFF
) {

    private val iconExecutor:
        ExecutorService =
        Executors.newFixedThreadPool(2)

    private val iconCache =
        object : LruCache<String, Bitmap>(
            calculateCacheSize()
        ) {
            override fun sizeOf(
                key: String,
                value: Bitmap
            ): Int {
                return value.byteCount / 1024
            }
        }

    init {
        setHasStableIds(true)
    }

    override fun getItemId(
        position: Int
    ): Long =
        getItem(position)
            .packageName
            .hashCode()
            .toLong()

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): Holder {
        return Holder(
            LayoutInflater.from(
                parent.context
            ).inflate(
                R.layout.item_per_app_application,
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(
        holder: Holder,
        position: Int
    ) {
        holder.bind(
            getItem(position)
        )
    }

    override fun onViewRecycled(
        holder: Holder
    ) {
        holder.recycle()
        super.onViewRecycled(holder)
    }

    fun replaceSelectedPackages(
        packages: Set<String>
    ) {
        if (
            selectedPackages == packages
        ) {
            return
        }

        selectedPackages.clear()
        selectedPackages.addAll(
            packages
        )

        notifyDataSetChanged()
    }

    fun close() {
        iconExecutor.shutdownNow()
        iconCache.evictAll()
    }

    inner class Holder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {

        private val icon =
            itemView.findViewById<ImageView>(
                R.id.app_icon
            )

        private val name =
            itemView.findViewById<TextView>(
                R.id.app_name
            )

        private val packageText =
            itemView.findViewById<TextView>(
                R.id.app_package
            )

        private val checkbox =
            itemView.findViewById<MaterialCheckBox>(
                R.id.app_checkbox
            )

        private var boundPackage:
            String? = null

        fun bind(
            item: PerAppEntry
        ) {
            boundPackage =
                item.packageName

            name.text =
                item.label

            packageText.text =
                item.packageName

            bindIcon(
                item.packageName
            )

            checkbox.setOnCheckedChangeListener(
                null
            )

            checkbox.isChecked =
                item.packageName in
                    selectedPackages

            checkbox.setOnCheckedChangeListener {
                    _,
                    checked ->

                setSelected(
                    item.packageName,
                    checked
                )
            }

            itemView.setOnClickListener {
                checkbox.isChecked =
                    !checkbox.isChecked
            }
        }

        private fun bindIcon(
            packageName: String
        ) {
            val cached =
                iconCache.get(
                    packageName
                )

            if (cached != null) {
                icon.setImageBitmap(
                    cached
                )
                return
            }

            icon.setImageDrawable(null)

            iconExecutor.execute {
                val bitmap =
                    runCatching {
                        packageManager
                            .getApplicationIcon(
                                packageName
                            )
                            .toBitmap36()
                    }.getOrNull()
                        ?: return@execute

                iconCache.put(
                    packageName,
                    bitmap
                )

                icon.post {
                    if (
                        boundPackage ==
                        packageName
                    ) {
                        icon.setImageBitmap(
                            bitmap
                        )
                    }
                }
            }
        }

        private fun setSelected(
            packageName: String,
            selected: Boolean
        ) {
            val previous =
                packageName in selectedPackages

            if (previous == selected) {
                return
            }

            val proposed =
                selectedPackages
                    .toMutableSet()
                    .apply {
                        if (selected) {
                            add(packageName)
                        } else {
                            remove(packageName)
                        }
                    }
                    .toSet()

            val accepted =
                onSelectionChangeRequested(
                    proposed
                )

            if (accepted) {
                selectedPackages.clear()
                selectedPackages.addAll(
                    proposed
                )
                return
            }

            checkbox.setOnCheckedChangeListener(
                null
            )

            checkbox.isChecked =
                previous

            checkbox.setOnCheckedChangeListener {
                    _,
                    checked ->

                setSelected(
                    packageName,
                    checked
                )
            }
        }

        fun recycle() {
            boundPackage = null
            icon.setImageDrawable(null)
            checkbox.setOnCheckedChangeListener(
                null
            )
            itemView.setOnClickListener(null)
        }

        private fun Drawable.toBitmap36():
            Bitmap {

            val density =
                itemView.resources
                    .displayMetrics
                    .density

            val size =
                (36f * density)
                    .toInt()
                    .coerceAtLeast(1)

            val bitmap =
                Bitmap.createBitmap(
                    size,
                    size,
                    Bitmap.Config.ARGB_8888
                )

            val canvas =
                Canvas(bitmap)

            setBounds(
                0,
                0,
                size,
                size
            )

            draw(canvas)

            return bitmap
        }
    }

    companion object {

        private val DIFF =
            object :
                DiffUtil.ItemCallback<
                    PerAppEntry
                >() {

                override fun areItemsTheSame(
                    oldItem: PerAppEntry,
                    newItem: PerAppEntry
                ): Boolean =
                    oldItem.packageName ==
                        newItem.packageName

                override fun areContentsTheSame(
                    oldItem: PerAppEntry,
                    newItem: PerAppEntry
                ): Boolean =
                    oldItem == newItem
            }

        private fun calculateCacheSize():
            Int {

            val maxMemoryKb =
                (
                    Runtime.getRuntime()
                        .maxMemory() / 1024L
                    ).toInt()

            return (
                maxMemoryKb / 32
                ).coerceAtLeast(2048)
        }
    }
}
