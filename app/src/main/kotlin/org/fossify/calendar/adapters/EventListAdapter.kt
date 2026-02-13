package org.fossify.calendar.adapters

import android.view.GestureDetector
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import android.content.Intent
import android.os.Bundle
import org.fossify.calendar.R
import org.fossify.calendar.activities.EventActivity
import org.fossify.calendar.activities.SimpleActivity
import org.fossify.calendar.activities.TaskActivity
import org.fossify.calendar.databinding.EventListItemBinding
import org.fossify.calendar.databinding.EventListSectionDayBinding
import org.fossify.calendar.databinding.EventListSectionMonthBinding
import org.fossify.calendar.dialogs.DeleteEventDialog
import org.fossify.calendar.extensions.*
import org.fossify.calendar.helpers.*
import org.fossify.calendar.models.ListEvent
import org.fossify.calendar.models.ListItem
import org.fossify.calendar.models.ListSectionDay
import org.fossify.calendar.models.ListSectionMonth
import org.fossify.commons.adapters.MyRecyclerViewAdapter
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.MEDIUM_ALPHA
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.interfaces.RefreshRecyclerViewListener
import org.fossify.commons.views.MyRecyclerView

class EventListAdapter(
    activity: SimpleActivity, var listItems: ArrayList<ListItem>, val allowLongClick: Boolean, val listener: RefreshRecyclerViewListener?,
    recyclerView: MyRecyclerView, itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick) {

    private val allDayString = resources.getString(R.string.all_day)
    private val displayDescription = activity.config.displayDescription
    private val replaceDescription = activity.config.replaceDescription
    private val dimPastEvents = activity.config.dimPastEvents
    private val dimCompletedTasks = activity.config.dimCompletedTasks
    private val now = getNowSeconds()
    private var use24HourFormat = activity.config.use24HourFormat
    private var currentItemsHash = listItems.hashCode()
    private var isPrintVersion = false
    private val mediumMargin = activity.resources.getDimension(org.fossify.commons.R.dimen.medium_margin).toInt()

    init {
        setupDragListener(true)
        val firstNonPastSectionIndex = listItems.indexOfFirst { it is ListSectionDay && !it.isPastSection }
        if (firstNonPastSectionIndex != -1) {
            activity.runOnUiThread {
                recyclerView.scrollToPosition(firstNonPastSectionIndex)
            }
        }
    }

    override fun getActionMenuId() = R.menu.cab_event_list

    override fun prepareActionMode(menu: Menu) {
        menu.findItem(R.id.cab_add_subitem)?.isVisible = isOneSelected()
        try {
            var currentClass: Class<*>? = MyRecyclerViewAdapter::class.java
            var actionMode: android.view.ActionMode? = null
            while (currentClass != null && actionMode == null) {
                val fields = currentClass.declaredFields
                for (field in fields) {
                    if (field.type.name.contains("ActionMode")) {
                        field.isAccessible = true
                        actionMode = field.get(this) as? android.view.ActionMode
                        if (actionMode != null) break
                    }
                }
                currentClass = currentClass.superclass
            }

            if (isOneSelected()) {
                actionMode?.title = null
                actionMode?.subtitle = null
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    override fun actionItemPressed(id: Int) {
        when (id) {
            R.id.cab_share -> shareEvents()
            R.id.cab_toggle_completion -> toggleCompletion()
            R.id.cab_delete -> askConfirmDelete()
            R.id.cab_add_subitem -> addSubItem()
        }
    }

    fun isOneSelected() = isOneItemSelected()

    fun addSubItem() {
        val selectedKey = selectedKeys.firstOrNull() ?: return
        val selectedEvent = listItems.find { (it as? ListEvent)?.hashCode() == selectedKey } as? ListEvent ?: return
        val eventId = selectedEvent.id

        ensureBackgroundThread {
            val event = activity.eventsDB.getEventOrTaskWithId(eventId) ?: return@ensureBackgroundThread
            val (updatedParentDescription, childDescriptionPrefix) = HierarchyHelper.prepareHierarchyForSubItem(event.description)

            val launchNewActivity = {
                activity.runOnUiThread {
                    val now = System.currentTimeMillis() / 1000L
                    val startTS = if (now < selectedEvent.startTS) selectedEvent.startTS else now
                    val bundle = Bundle()
                    bundle.putLong(NEW_EVENT_START_TS, startTS)
                    bundle.putString("description", childDescriptionPrefix)

                    val intent = if (event.isTask()) {
                        Intent(activity, TaskActivity::class.java)
                    } else {
                        Intent(activity, EventActivity::class.java).apply {
                            action = Intent.ACTION_INSERT
                            putExtra("beginTime", startTS * 1000L)
                            putExtra("endTime", (startTS + activity.config.defaultDuration * 60) * 1000L)
                        }
                    }

                    intent.putExtras(bundle)
                    activity.launchActivityIntent(intent)
                    finishActMode()
                }
            }

            if (updatedParentDescription != null) {
                event.description = updatedParentDescription
                activity.eventsHelper.updateEvent(event, updateAtCalDAV = true, showToasts = false) {
                    launchNewActivity()
                }
            } else {
                launchNewActivity()
            }
        }
    }

    private fun toggleCompletion() {
        val selectedEvents = listItems.filter { selectedKeys.contains((it as? ListEvent)?.hashCode()) } as List<ListEvent>
        val eventIds = selectedEvents.map { it.id }
        ensureBackgroundThread {
            val events = activity.eventsDB.getEventsOrTasksWithIds(eventIds)
            events.forEach { event ->
                val listEvent = selectedEvents.find { it.id == event.id } ?: return@forEach
                val isCompleted = if (event.isTask()) !listEvent.isTaskCompleted else !event.title.startsWith(COMPLETION_PREFIX)
                activity.toggleCompletion(event, isCompleted, recursive = true, startTS = listEvent.startTS)
            }

            activity.runOnUiThread {
                (activity as? RefreshRecyclerViewListener)?.refreshItems()
                listener?.refreshItems()
                finishActMode()
            }
        }
    }

    private fun toggleSingleItemCompletion(listEvent: ListEvent) {
        val eventId = listEvent.id
        ensureBackgroundThread {
            val event = activity.eventsDB.getEventOrTaskWithId(eventId) ?: return@ensureBackgroundThread
            val isCompleted = if (event.isTask()) !listEvent.isTaskCompleted else !event.title.startsWith(COMPLETION_PREFIX)
            activity.toggleCompletion(event, isCompleted, recursive = true, startTS = listEvent.startTS)

            activity.runOnUiThread {
                (activity as? RefreshRecyclerViewListener)?.refreshItems()
                listener?.refreshItems()
            }
        }
    }

    override fun getSelectableItemCount() = listItems.filterIsInstance<ListEvent>().size

    override fun getIsItemSelectable(position: Int) = listItems.getOrNull(position) is ListEvent

    override fun getItemSelectionKey(position: Int) = (listItems.getOrNull(position) as? ListEvent)?.hashCode()

    override fun getItemKeyPosition(key: Int) = listItems.indexOfFirst { (it as? ListEvent)?.hashCode() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyRecyclerViewAdapter.ViewHolder {
        val layoutInflater = activity.layoutInflater
        val binding = when (viewType) {
            ITEM_SECTION_DAY -> EventListSectionDayBinding.inflate(layoutInflater, parent, false)
            ITEM_SECTION_MONTH -> EventListSectionMonthBinding.inflate(layoutInflater, parent, false)
            else -> EventListItemBinding.inflate(layoutInflater, parent, false)
        }

        return createViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: MyRecyclerViewAdapter.ViewHolder, position: Int) {
        val listItem = listItems[position]
        holder.bindView(listItem, allowSingleClick = true, allowLongClick = allowLongClick && listItem is ListEvent) { itemView, _ ->
            when (listItem) {
                is ListSectionDay -> setupListSectionDay(itemView, listItem)
                is ListEvent -> setupListEvent(itemView, listItem)
                is ListSectionMonth -> setupListSectionMonth(itemView, listItem)
            }
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = listItems.size

    override fun getItemViewType(position: Int) = when {
        listItems[position] is ListEvent -> ITEM_EVENT
        listItems[position] is ListSectionDay -> ITEM_SECTION_DAY
        else -> ITEM_SECTION_MONTH
    }

    fun toggle24HourFormat(use24HourFormat: Boolean) {
        this.use24HourFormat = use24HourFormat
        notifyDataSetChanged()
    }

    fun updateListItems(newListItems: ArrayList<ListItem>) {
        if (newListItems.hashCode() != currentItemsHash) {
            currentItemsHash = newListItems.hashCode()
            listItems = newListItems.clone() as ArrayList<ListItem>
            recyclerView.resetItemCount()
            notifyDataSetChanged()
            finishActMode()
        }
    }

    fun togglePrintMode() {
        isPrintVersion = !isPrintVersion
        textColor = if (isPrintVersion) {
            resources.getColor(org.fossify.commons.R.color.theme_light_text_color)
        } else {
            activity.getProperTextColor()
        }
        notifyDataSetChanged()
    }

    private fun setupListEvent(view: View, listEvent: ListEvent) {
        EventListItemBinding.bind(view).apply {
            eventItemHolder.isSelected = selectedKeys.contains(listEvent.hashCode())
            eventItemHolder.background.applyColorFilter(textColor)
            val gestureDetector = GestureDetector(activity, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    toggleSingleItemCompletion(listEvent)
                    return true
                }

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    if (selectedKeys.isNotEmpty()) {
                        view.performClick()
                    } else {
                        itemClick(listEvent)
                    }
                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                    view.performLongClick()
                }
            })

            eventItemHolder.setOnTouchListener { _, motionEvent ->
                gestureDetector.onTouchEvent(motionEvent)
                true
            }

            eventItemTitle.text = listEvent.title
            eventItemTitle.checkViewStrikeThrough(listEvent.shouldStrikeThrough())
            var timeText = if (listEvent.isAllDay) allDayString else Formatter.getTimeFromTS(activity, listEvent.startTS)
            if (listEvent.isGroupedView) {
                val date = Formatter.getDateFromTS(listEvent.startTS)
                timeText = "$date, $timeText"
            }
            eventItemTime.text = timeText
            if (listEvent.startTS != listEvent.endTS) {
                if (!listEvent.isAllDay) {
                    eventItemTime.text = "${eventItemTime.text} - ${Formatter.getTimeFromTS(activity, listEvent.endTS)}"
                }

                val startCode = Formatter.getDayCodeFromTS(listEvent.startTS)
                val endCode = Formatter.getDayCodeFromTS(listEvent.endTS)
                if (startCode != endCode) {
                    eventItemTime.text = "${eventItemTime.text} (${Formatter.getDateDayTitle(endCode)})"
                }
            }

            eventItemDescription.text = if (replaceDescription) listEvent.location else listEvent.description.replace("\n", " ")
            eventItemDescription.beVisibleIf(displayDescription && eventItemDescription.text.isNotEmpty())
            eventItemColorBar.background.applyColorFilter(listEvent.color)

            var newTextColor = textColor
            if (listEvent.isAllDay || listEvent.startTS <= now && listEvent.endTS <= now) {
                if (listEvent.isAllDay && Formatter.getDayCodeFromTS(listEvent.startTS) == Formatter.getDayCodeFromTS(now) && !isPrintVersion) {
                    newTextColor = properPrimaryColor
                }

                val adjustAlpha = if (listEvent.isTask) {
                    dimCompletedTasks && listEvent.isTaskCompleted
                } else {
                    dimPastEvents && listEvent.isPastEvent && !isPrintVersion
                }
                if (adjustAlpha) {
                    newTextColor = newTextColor.adjustAlpha(MEDIUM_ALPHA)
                }
            } else if (listEvent.startTS <= now && listEvent.endTS >= now && !isPrintVersion) {
                newTextColor = properPrimaryColor
            }

            eventItemTime.setTextColor(newTextColor)
            eventItemTitle.setTextColor(newTextColor)
            eventItemDescription.setTextColor(newTextColor)
            eventItemTaskImage.applyColorFilter(newTextColor)
            eventItemTaskImage.beVisibleIf(listEvent.isTask)

            val startMargin = if (listEvent.isTask) {
                0
            } else {
                mediumMargin
            }

            (eventItemTitle.layoutParams as ConstraintLayout.LayoutParams).marginStart = startMargin

            val indentation = if (listEvent.isGroupedView) {
                ((listEvent.hierarchyLevel - 1) * activity.resources.getDimension(org.fossify.commons.R.dimen.activity_margin) * 1.5).toInt()
            } else {
                0
            }
            (eventItemHolder.layoutParams as ViewGroup.MarginLayoutParams).marginStart = activity.resources.getDimension(org.fossify.commons.R.dimen.activity_margin).toInt() + indentation
        }
    }

    private fun setupListSectionDay(view: View, listSectionDay: ListSectionDay) {
        EventListSectionDayBinding.bind(view).eventSectionTitle.apply {
            text = listSectionDay.title
            val dayColor = if (listSectionDay.isToday) properPrimaryColor else textColor
            setTextColor(dayColor)
        }
    }

    private fun setupListSectionMonth(view: View, listSectionMonth: ListSectionMonth) {
        EventListSectionMonthBinding.bind(view).eventSectionTitle.apply {
            text = listSectionMonth.title
            setTextColor(properPrimaryColor)
        }
    }

    private fun shareEvents() = activity.shareEvents(getSelectedEventIds())

    private fun getSelectedEventIds() =
        listItems.filter { it is ListEvent && selectedKeys.contains(it.hashCode()) }.map { (it as ListEvent).id }.toMutableList() as ArrayList<Long>

    private fun askConfirmDelete() {
        val eventIds = getSelectedEventIds()
        val eventsToDelete = listItems.filter { selectedKeys.contains((it as? ListEvent)?.hashCode()) } as List<ListEvent>
        val timestamps = eventsToDelete.mapNotNull { (it as? ListEvent)?.startTS }

        val hasRepeatableEvent = eventsToDelete.any { it.isRepeatable }
        DeleteEventDialog(activity, eventIds, hasRepeatableEvent) {
            listItems.removeAll(eventsToDelete)

            ensureBackgroundThread {
                val nonRepeatingEventIDs = eventsToDelete.filter { !it.isRepeatable }.map { it.id }.toMutableList()
                activity.eventsHelper.deleteEvents(nonRepeatingEventIDs, true)

                val repeatingEventIDs = eventsToDelete.filter { it.isRepeatable }.map { it.id }
                activity.handleEventDeleting(repeatingEventIDs, timestamps, it)
                activity.runOnUiThread {
                    (activity as? RefreshRecyclerViewListener)?.refreshItems()
                    listener?.refreshItems()
                    finishActMode()
                }
            }
        }
    }
}
