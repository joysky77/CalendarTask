import re
import os

filepath = r"d:\MyDrive\My\andriod\AndroidStudioProjects\Calendar\app\src\main\kotlin\org\fossify\calendar\extensions\Context.kt"

new_func = """fun Context.getEventListItems(
    events: List<Event>,
    contactEvents: List<Event> = events, // Full context events (for hierarchy resolution) containing potential parents
    addSectionDays: Boolean = true,
    addSectionMonths: Boolean = true
): ArrayList<ListItem> {
    val listItems = ArrayList<ListItem>(events.size)
    val showSubItems = config.showSubItems

    // 1. Prepare hierarchy info for ALL context events (to find parents)
    val contextWithHierarchy = contactEvents.map { it to HierarchyHelper.extractHierarchy(it.description) }
    
    // 2. Sort context by time (crucial for "Nearest" logic)
    val contextSorted = contextWithHierarchy.sortedWith(compareBy<Pair<Event, HierarchyInfo?>> { (event, _) ->
        if (event.getIsAllDay()) Formatter.getDayStartTS(Formatter.getDayCodeFromTS(event.startTS)) - 1 else event.startTS
    }.thenBy { (event, _) ->
        if (event.getIsAllDay()) Formatter.getDayEndTS(Formatter.getDayCodeFromTS(event.endTS)) else event.endTS
    }.thenBy { it.first.title })

    // 3. Build the Parent-Child Map using strict logic on the FULL context
    val childrenMap = mutableMapOf<Long, MutableList<Triple<Event, HierarchyInfo, Boolean>>>()
    val hasVisibleParent = mutableSetOf<Long>()
    val visibleIds = events.mapNotNull { it.id }.toSet()
    
    // We maintain state for the ENTIRE sorted stream to allow "cross-tag" relationships
    val latestEvents = mutableMapOf<Int, Event>() 
    val latestTagEvents = mutableMapOf<Int, MutableMap<String, Event>>() 

    contextSorted.forEach { (event, hierarchy) ->
        if (hierarchy != null) {
            val level = hierarchy.level
            val tag = hierarchy.tag
            
            // Register self
            latestEvents[level] = event
            latestTagEvents.getOrPut(level) { mutableMapOf() }[tag] = event
            
            if (level > 1) {
                val parentLevel = level - 1
                var parent: Event? = null
                
                val levelTags = latestTagEvents[parentLevel]
                if (levelTags != null) {
                    parent = levelTags[tag]
                    if (parent == null) {
                        val match = levelTags.entries.find { (pTag, _) -> tag.startsWith(pTag) }
                        parent = match?.value
                    }
                }
                
                if (parent == null) {
                    parent = latestEvents[parentLevel]
                }
                
                if (parent != null) {
                    // 6-month constraint
                    val parentLimit = DateTime(parent.startTS * 1000L).plusMonths(6).seconds()
                    
                    if (event.startTS >= parent.startTS && event.startTS <= parentLimit) {
                         childrenMap.getOrPut(parent.id!!) { mutableListOf() }.add(Triple(event, hierarchy, true))
                         if (visibleIds.contains(parent.id)) {
                             hasVisibleParent.add(event.id!!)
                         }
                    }
                }
            }
        }
    }    

    // 4. Generate Final List
    val finalDisplayItems = mutableListOf<Triple<Event, HierarchyInfo?, Boolean>>()
    val processedAsAnchored = mutableSetOf<Long>()

    // Helper to add sub-items recursively (Anchored/Indented)
    fun addAnchoredChildren(parentEvent: Event) {
        val children = childrenMap[parentEvent.id]
        children?.forEach { (cEvent, cHierarchy, _) ->
            if (visibleIds.contains(cEvent.id)) {
                finalDisplayItems.add(Triple(cEvent, cHierarchy, true))
                processedAsAnchored.add(cEvent.id!!)
                addAnchoredChildren(cEvent)
            }
        }
    }

    // Main Loop
    events.forEach { event ->
        val hierarchy = HierarchyHelper.extractHierarchy(event.description)
        finalDisplayItems.add(Triple(event, hierarchy, false)) 
        
        if (showSubItems && !hasVisibleParent.contains(event.id) && !processedAsAnchored.contains(event.id)) {
            addAnchoredChildren(event)
        }
    }
    
    // Generate List Items
    var prevCode = ""
    var prevMonthLabel = ""
    val now = getNowSeconds()
    val todayCode = Formatter.getDayCodeFromTS(now)

    finalDisplayItems.forEach { (event, hierarchy, isGroupedView) ->
        val code = Formatter.getDayCodeFromTS(event.startTS)
        
        if (!isGroupedView) {
            if (addSectionMonths) {
                val monthLabel = Formatter.getLongMonthYear(this, code)
                if (monthLabel != prevMonthLabel) {
                    val listSectionMonth = ListSectionMonth(monthLabel)
                    listItems.add(listSectionMonth)
                    prevMonthLabel = monthLabel
                }
            }

            if (code != prevCode && addSectionDays) {
                val day = Formatter.getDateDayTitle(code)
                val isToday = code == todayCode
                val listSectionDay = ListSectionDay(day, code, isToday, !isToday && event.startTS < now)
                listItems.add(listSectionDay)
                prevCode = code
            }
        }

        val listEvent =
            ListEvent(
                id = event.id ?: 0L,
                startTS = event.startTS,
                endTS = event.endTS,
                title = event.title,
                description = event.description,
                isAllDay = event.getIsAllDay(),
                color = event.color,
                location = event.location,
                isPastEvent = event.startTS < now,
                isRepeatable = event.repeatInterval > 0,
                isTask = event.isTask(),
                isTaskCompleted = isTaskCompleted(event),
                isAttendeeInviteDeclined = event.isAttendeeInviteDeclined(),
                isEventCanceled = event.isEventCanceled(),
                hierarchyLevel = hierarchy?.level ?: 1,
                hierarchyTag = hierarchy?.tag ?: "",
                isGroupedView = isGroupedView
            )
        listItems.add(listEvent)
    }
    return listItems
}"""

with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

# Regular expression to find the getEventListItems function
# It starts with "fun Context.getEventListItems" and ends with "return listItems\n}"
pattern = r"fun Context\.getEventListItems\(.*?\n    return listItems\n\}"

# Use regex to replace
new_content = re.sub(pattern, new_func, content, flags=re.DOTALL)

with open(filepath, 'w', encoding='utf-8') as f:
    f.write(new_content)

print("Successfully updated Context.kt")
