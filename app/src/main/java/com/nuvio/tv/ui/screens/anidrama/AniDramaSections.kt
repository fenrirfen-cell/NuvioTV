package com.nuvio.tv.ui.screens.anidrama

/** One block of a sectioned grid: a header ("November 2026", "This week") and its posters. */
internal data class AniDramaSection(
    val key: String,
    val title: String,
    val note: String?,
    val items: List<AniDramaItem>,
)

internal object AniDramaSections {
    fun build(items: List<AniDramaItem>, grouping: AniDramaGrouping): List<AniDramaSection> = when (grouping) {
        AniDramaGrouping.None -> listOf(AniDramaSection("all", "", null, items))
        AniDramaGrouping.MonthNewest -> months(items, newestFirst = true)
        AniDramaGrouping.MonthSoonest -> months(items, newestFirst = false)
        AniDramaGrouping.Week -> weeks(items)
    }

    /** "2027-01-05" -> "2027-01"; "2027" -> "2027-13" so year-only dates come after that year's months. */
    private fun monthKey(date: String?): String? = when {
        date.isNullOrBlank() -> null
        date.length >= 7 -> date.take(7)
        date.length == 4 -> "$date-13"
        else -> null
    }

    private fun months(items: List<AniDramaItem>, newestFirst: Boolean): List<AniDramaSection> {
        val thisMonth = AniDramaTime.todayIso().take(7)
        val sorted = if (newestFirst) {
            items.sortedByDescending { it.groupDate.orEmpty() }
        } else {
            items.sortedBy { it.groupDate ?: "\uFFFF" }
        }
        val grouped = LinkedHashMap<String?, MutableList<AniDramaItem>>()
        sorted.forEach { grouped.getOrPut(monthKey(it.groupDate)) { mutableListOf() }.add(it) }
        val dated = grouped.filterKeys { it != null }.entries.sortedWith(
            if (newestFirst) compareByDescending { it.key } else compareBy { it.key },
        )
        val sections = dated.map { (key, list) ->
            val year = key!!.take(4)
            val month = key.substring(5, 7).toInt()
            val title = when {
                month == 13 -> if (newestFirst) year else "Later in $year"
                !newestFirst && key == thisMonth -> "Later this month"
                else -> "${AniDramaTime.monthName(month)} $year"
            }
            AniDramaSection("m-$key", title, countLabel(list.size), list)
        }
        val undated = grouped[null].orEmpty()
        return if (undated.isEmpty()) sections else sections + AniDramaSection("m-none", "Date not announced", countLabel(undated.size), undated)
    }

    private fun weeks(items: List<AniDramaItem>): List<AniDramaSection> {
        val monday = AniDramaTime.mondayOfThisWeek()
        val lastMonday = AniDramaTime.addDays(monday, -7)
        val sorted = items.sortedByDescending { it.groupDate.orEmpty() }
        val thisWeek = sorted.filter { (it.groupDate ?: "") >= monday }
        val lastWeek = sorted.filter { (it.groupDate ?: "").let { d -> d >= lastMonday && d < monday } }
        val earlier = sorted.filter { (it.groupDate ?: "") < lastMonday }
        return listOfNotNull(
            thisWeek.takeIf { it.isNotEmpty() }?.let { AniDramaSection("w-this", "This week", "new episodes", it) },
            lastWeek.takeIf { it.isNotEmpty() }?.let { AniDramaSection("w-last", "Last week", null, it) },
            earlier.takeIf { it.isNotEmpty() }?.let { AniDramaSection("w-earlier", "Earlier", "between episodes", it) },
        )
    }

    private fun countLabel(count: Int) = if (count == 1) "1 show" else "$count shows"

    /** Caption under a poster for a date: "Wed, Nov 19", or "Jan 2027" / "2027" when the day isn't known. */
    fun dateCaption(date: String): String = when {
        date.length >= 10 -> AniDramaTime.shortDayLabel(date)
        date.length >= 7 -> "${AniDramaTime.monthName(date.substring(5, 7).toInt()).take(3)} ${date.take(4)}"
        else -> date
    }
}
