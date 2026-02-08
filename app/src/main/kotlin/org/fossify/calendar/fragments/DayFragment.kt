package org.fossify.calendar.fragments

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.fossify.calendar.R
import org.fossify.calendar.activities.MainActivity
import org.fossify.calendar.activities.SimpleActivity
import org.fossify.calendar.adapters.EventListAdapter
import org.fossify.calendar.databinding.FragmentDayBinding
import org.fossify.calendar.databinding.TopNavigationBinding
import org.fossify.calendar.extensions.*
import org.fossify.calendar.helpers.*
import org.fossify.calendar.interfaces.NavigationListener
import org.fossify.calendar.models.Event
import org.fossify.calendar.models.ListEvent
import org.fossify.calendar.models.ListItem
import org.fossify.commons.extensions.*
import org.fossify.commons.interfaces.RefreshRecyclerViewListener
import org.joda.time.DateTime

class DayFragment : Fragment(), RefreshRecyclerViewListener {
    var mListener: NavigationListener? = null
    private var mTextColor = 0
    private var mDayCode = ""
    private var lastHash = 0

    private lateinit var binding: FragmentDayBinding
    private lateinit var topNavigationBinding: TopNavigationBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentDayBinding.inflate(inflater, container, false)
        topNavigationBinding = TopNavigationBinding.bind(binding.root)
        mDayCode = requireArguments().getString(DAY_CODE)!!
        setupButtons()
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        updateCalendar()
    }

    private fun setupButtons() {
        mTextColor = requireContext().getProperTextColor()

        topNavigationBinding.topLeftArrow.apply {
            applyColorFilter(mTextColor)
            background = null
            setOnClickListener {
                mListener?.goLeft()
            }

            val pointerLeft = requireContext().getDrawable(org.fossify.commons.R.drawable.ic_chevron_left_vector)
            pointerLeft?.isAutoMirrored = true
            setImageDrawable(pointerLeft)
            contentDescription = getString(R.string.accessibility_previous_day)
        }

        topNavigationBinding.topRightArrow.apply {
            applyColorFilter(mTextColor)
            background = null
            setOnClickListener {
                mListener?.goRight()
            }

            val pointerRight = requireContext().getDrawable(org.fossify.commons.R.drawable.ic_chevron_right_vector)
            pointerRight?.isAutoMirrored = true
            setImageDrawable(pointerRight)
            contentDescription = getString(R.string.accessibility_next_day)
        }

        val day = Formatter.getDayTitle(requireContext(), mDayCode)
        topNavigationBinding.topValue.apply {
            text = day
            contentDescription = text
            setOnClickListener {
                (activity as MainActivity).showGoToDateDialog()
            }
            setTextColor(context.getProperTextColor())
        }
    }

    fun updateCalendar() {
        val dayStartTS = Formatter.getDayStartTS(mDayCode)
        val dayEndTS = Formatter.getDayEndTS(mDayCode)
        
        // Fetch context window (e.g. +/- 1 month) to find parents for hierarchy
        // This mimics EventList behavior partially without overloading DayView performance
        val contextStartTS = DateTime(dayStartTS * 1000L).minusYears(1).seconds()
        val contextEndTS = DateTime(dayEndTS * 1000L).plusYears(1).seconds()
        
        context?.eventsHelper?.getEvents(contextStartTS, contextEndTS) { fullList ->
            // Filter strictly for this day for display
            val dayEvents = fullList.filter { it.startTS in dayStartTS..dayEndTS || it.endTS in dayStartTS..dayEndTS || (it.startTS < dayStartTS && it.endTS > dayEndTS)}
            // Use fullList as contactEvents (Context)
            receivedEvents(dayEvents, fullList)
        }
    }

    private fun receivedEvents(events: List<Event>, contextEvents: List<Event>) {
        val newHash = events.hashCode()
        if (newHash == lastHash || !isAdded) {
            return
        }
        lastHash = newHash

        val listItems = requireContext().getEventListItems(events, contextEvents, addSectionDays = false, addSectionMonths = false)

        activity?.runOnUiThread {
            updateEvents(listItems)
        }
    }

    private fun updateEvents(listItems: ArrayList<ListItem>) {
        if (activity == null)
            return

        EventListAdapter(activity as SimpleActivity, listItems, true, this, binding.dayEvents) {
            if (it is ListEvent) {
                editEvent(it)
            }
        }.apply {
            binding.dayEvents.adapter = this
        }

        if (requireContext().areSystemAnimationsEnabled) {
            binding.dayEvents.scheduleLayoutAnimation()
        }
    }

    private fun editEvent(listEvent: ListEvent) {
        Intent(context, getActivityToOpen(listEvent.isTask)).apply {
            putExtra(EVENT_ID, listEvent.id)
            putExtra(EVENT_OCCURRENCE_TS, listEvent.startTS)
            putExtra(IS_TASK_COMPLETED, listEvent.isTaskCompleted)
            startActivity(this)
        }
    }

    fun printCurrentView() {
        topNavigationBinding.apply {
            topLeftArrow.beGone()
            topRightArrow.beGone()
            topValue.setTextColor(resources.getColor(org.fossify.commons.R.color.theme_light_text_color))
            (binding.dayEvents.adapter as? EventListAdapter)?.togglePrintMode()

            Handler().postDelayed({
                requireContext().printBitmap(binding.dayHolder.getViewBitmap())

                Handler().postDelayed({
                    topLeftArrow.beVisible()
                    topRightArrow.beVisible()
                    topValue.setTextColor(requireContext().getProperTextColor())
                    (binding.dayEvents.adapter as? EventListAdapter)?.togglePrintMode()
                }, 1000)
            }, 1000)
        }
    }

    override fun refreshItems() {
        updateCalendar()
    }
}
