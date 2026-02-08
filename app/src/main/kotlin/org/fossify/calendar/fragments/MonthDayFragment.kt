package org.fossify.calendar.fragments

import android.content.Context
import android.content.res.Resources
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.fossify.calendar.activities.MainActivity
import org.fossify.calendar.activities.SimpleActivity
import org.fossify.calendar.adapters.EventListAdapter
import org.fossify.calendar.databinding.FragmentMonthDayBinding
import org.fossify.calendar.extensions.*
import org.fossify.calendar.helpers.Config
import org.fossify.calendar.helpers.DAY_CODE
import org.fossify.calendar.helpers.Formatter
import org.fossify.calendar.helpers.Formatter.YEAR_PATTERN
import org.fossify.calendar.helpers.MonthlyCalendarImpl
import org.fossify.calendar.interfaces.MonthlyCalendar
import org.fossify.calendar.interfaces.NavigationListener
import org.fossify.calendar.models.DayMonthly
import org.fossify.calendar.models.Event
import org.fossify.calendar.models.ListEvent
import org.fossify.commons.extensions.areSystemAnimationsEnabled
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.interfaces.RefreshRecyclerViewListener
import org.joda.time.DateTime

class MonthDayFragment : Fragment(), MonthlyCalendar, RefreshRecyclerViewListener {
    private var mShowWeekNumbers = false
    private var mDayCode = ""
    private var mSelectedDayCode = ""
    private var mPackageName = ""
    private var mLastHash = 0L
    private var mCalendar: MonthlyCalendarImpl? = null
    private var mListEvents = ArrayList<Event>()

    var listener: NavigationListener? = null

    private lateinit var mRes: Resources
    private lateinit var binding: FragmentMonthDayBinding
    private lateinit var mConfig: Config

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentMonthDayBinding.inflate(inflater, container, false)
        mRes = resources
        mDayCode = arguments?.getString(DAY_CODE) ?: ""
        if (mDayCode.isEmpty()) return binding.root

        val shownMonthDateTime = Formatter.getDateTimeFromCode(mDayCode)
        binding.monthDaySelectedDayLabel.apply {
            text = getMonthLabel(shownMonthDateTime)
            setOnClickListener {
                (activity as MainActivity).showGoToDateDialog()
            }
        }

        mConfig = requireContext().config
        storeStateVariables()
        setupButtons()
        mCalendar = MonthlyCalendarImpl(this, requireContext())
        return binding.root
    }

    override fun onPause() {
        super.onPause()
        storeStateVariables()
    }

    override fun onResume() {
        super.onResume()
        if (mConfig.showWeekNumbers != mShowWeekNumbers) {
            mLastHash = -1L
        }

        mCalendar?.apply {
            mTargetDate = Formatter.getDateTimeFromCode(mDayCode)
            getDays(false)    // prefill the screen asap, even if without events
        }

        storeStateVariables()
        updateCalendar()
    }

    private fun storeStateVariables() {
        mConfig.apply {
            mShowWeekNumbers = showWeekNumbers
        }
    }

    fun updateCalendar() {
        mCalendar?.updateMonthlyCalendar(Formatter.getDateTimeFromCode(mDayCode))
    }

    override fun updateMonthlyCalendar(context: Context, month: String, days: ArrayList<DayMonthly>, checkedEvents: Boolean, currTargetDate: DateTime) {
        val newHash = month.hashCode() + days.hashCode().toLong()
        if ((mLastHash != 0L && !checkedEvents) || (mLastHash == newHash && !checkedEvents)) {
            return
        }

        mLastHash = newHash

        activity?.runOnUiThread {
            if (isAdded && activity != null) {
                binding.monthDayViewWrapper.updateDays(days, false) {
                    mSelectedDayCode = it.code
                    updateVisibleEvents()
                }
            }
        }

        refreshItems()
    }

    private fun updateVisibleEvents() {
        if (activity == null) {
            return
        }

        val selectedDayCode = mSelectedDayCode
        val dayCode = mDayCode
        val listEvents = mListEvents
        val context = context ?: return

        ensureBackgroundThread {
            val filtered = listEvents.filter {
                if (selectedDayCode.isEmpty()) {
                    val shownMonthDateTime = Formatter.getDateTimeFromCode(dayCode)
                    val startDateTime = Formatter.getDateTimeFromTS(it.startTS)
                    shownMonthDateTime.year == startDateTime.year && shownMonthDateTime.monthOfYear == startDateTime.monthOfYear
                } else {
                    val selectionDate = Formatter.getDateTimeFromCode(selectedDayCode).toLocalDate()
                    val startDate = Formatter.getDateFromTS(it.startTS)
                    val endDate = Formatter.getDateFromTS(it.endTS)
                    selectionDate in startDate..endDate
                }
            }

            val listItems = activity?.getEventListItems(filtered, listEvents, selectedDayCode.isEmpty(), false) ?: ArrayList()
            val date = if (selectedDayCode.isNotEmpty()) {
                Formatter.getDateFromCode(context, selectedDayCode, false)
            } else {
                null
            }

            activity?.runOnUiThread {
                if (activity != null && isAdded) {
                    if (date != null) {
                        binding.monthDaySelectedDayLabel.text = date
                    }

                    binding.monthDayEventsList.beVisibleIf(listItems.isNotEmpty())
                    binding.monthDayNoEventsPlaceholder.beVisibleIf(listItems.isEmpty())

                    val currAdapter = binding.monthDayEventsList.adapter
                    if (currAdapter == null) {
                        val activity = activity as? SimpleActivity
                        if (activity != null) {
                            EventListAdapter(activity, listItems, true, this, binding.monthDayEventsList) {
                                if (it is ListEvent) {
                                    activity.editEvent(it)
                                }
                            }.apply {
                                binding.monthDayEventsList.adapter = this
                            }
                        }

                        if (getContext()?.areSystemAnimationsEnabled == true) {
                            binding.monthDayEventsList.scheduleLayoutAnimation()
                        }
                    } else {
                        (currAdapter as EventListAdapter).updateListItems(listItems)
                    }
                }
            }
        }
    }

    private fun setupButtons() {
        val textColor = requireContext().getProperTextColor()
        binding.apply {
            monthDaySelectedDayLabel.setTextColor(textColor)
            monthDayNoEventsPlaceholder.setTextColor(textColor)
        }
    }

    fun printCurrentView() {}

    fun getNewEventDayCode() = mSelectedDayCode.ifEmpty { null }

    private fun getMonthLabel(shownMonthDateTime: DateTime): String {
        val activity = activity ?: return ""
        var month = Formatter.getMonthName(activity, shownMonthDateTime.monthOfYear)
        val targetYear = shownMonthDateTime.toString(YEAR_PATTERN)
        if (targetYear != DateTime().toString(YEAR_PATTERN)) {
            month += " $targetYear"
        }
        return month
    }

    override fun refreshItems() {
        val startDateTime = Formatter.getLocalDateTimeFromCode(mDayCode).minusYears(1)
        val endDateTime = startDateTime.plusMonths(25) // Cover 1 year back + 1 year forward + buffer
        activity?.eventsHelper?.getEvents(startDateTime.seconds(), endDateTime.seconds()) { events ->
            mListEvents = events
            updateVisibleEvents()
        }
    }
}
