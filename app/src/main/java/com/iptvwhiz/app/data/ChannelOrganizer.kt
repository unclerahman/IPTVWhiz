package com.iptvwhiz.app.data

import com.iptvwhiz.app.data.db.Channel
import com.iptvwhiz.app.data.db.GroupSetting

/** A group as shown to the user, with their edits applied. [key] never changes when renamed. */
data class ChannelGroup(
    val key: String,
    val displayName: String,
    val visibleCount: Int,
    val totalCount: Int,
    val hidden: Boolean,
    val isCustom: Boolean,
    val sortOrder: Double,
)

/** Everything one playlist shows, built from channels + group settings. */
class OrganizedPlaylist(
    val channels: List<Channel>,
    /** All groups in display order, hidden ones included (for the manage screen). */
    val groups: List<ChannelGroup>,
) {
    private val groupIndex = groups.withIndex().associate { (i, g) -> g.key to i }
    private val hiddenGroups = groups.filter { it.hidden }.map { it.key }.toSet()

    /** Groups for browsing: not hidden and with something to watch. */
    val visibleGroups: List<ChannelGroup> = groups.filter { !it.hidden && it.visibleCount > 0 }

    private fun isWatchable(channel: Channel) = !channel.hidden && channel.groupTitle !in hiddenGroups

    private val displayOrder = compareBy<Channel>(
        { groupIndex[it.groupTitle] ?: Int.MAX_VALUE },
        { it.sortOrder },
        { it.number },
    )

    val visibleCount: Int = channels.count(::isWatchable)

    val favorites: List<Channel> = channels.filter { it.favorite && isWatchable(it) }.sortedWith(displayOrder)

    /**
     * What browsing [key] shows: [ALL] = every watchable channel, [FAVORITES], or one group.
     * A group the user opens directly shows its visible channels even if the group is hidden.
     */
    fun browse(key: String): List<Channel> = when (key) {
        ALL -> channels.filter(::isWatchable).sortedWith(displayOrder)
        FAVORITES -> favorites
        else -> channels.filter { it.groupTitle == key && !it.hidden }.sortedWith(displayOrder)
    }

    /** Every channel of a group for editing, hidden ones included, in display order. */
    fun manage(groupKey: String): List<Channel> =
        channels.filter { it.groupTitle == groupKey }.sortedWith(displayOrder)

    companion object {
        /** Pseudo-group keys. Real group keys come from playlists and can't start with \u0000. */
        const val ALL = "\u0000all"
        const val FAVORITES = "\u0000favorites"

        fun build(channels: List<Channel>, settings: List<GroupSetting>): OrganizedPlaylist {
            val byGroup = channels.groupBy { it.groupTitle }
            val settingsByName = settings.associateBy { it.name }
            val keys = (byGroup.keys + settings.filter { it.isCustom }.map { it.name }).distinct()
            val groups = keys.map { key ->
                val members = byGroup[key].orEmpty()
                val setting = settingsByName[key]
                ChannelGroup(
                    key = key,
                    displayName = setting?.customName ?: key,
                    visibleCount = members.count { !it.hidden },
                    totalCount = members.size,
                    hidden = setting?.hidden ?: false,
                    isCustom = setting?.isCustom ?: false,
                    // Provider groups keep the provider's order until moved.
                    sortOrder = setting?.sortOrder ?: members.minOfOrNull { it.number }?.toDouble() ?: Double.MAX_VALUE,
                )
            }.sortedWith(compareBy({ it.sortOrder }, { it.displayName.lowercase() }))
            return OrganizedPlaylist(channels, groups)
        }
    }
}

/** Fractional positions: moving one item rewrites only that item (PLAN.md §15). */
object Positions {
    private const val MIN_GAP = 1e-6

    /** A position between [before] and [after] (either may be absent), or null when they're too close and the list needs renumbering. */
    fun between(before: Double?, after: Double?): Double? = when {
        before == null && after == null -> 0.0
        before == null -> after!! - 1.0
        after == null -> before + 1.0
        after - before > MIN_GAP -> (before + after) / 2.0
        else -> null
    }
}
