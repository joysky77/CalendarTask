package org.fossify.calendar.extensions

import org.fossify.calendar.helpers.COMPLETION_PREFIX
import org.fossify.calendar.models.MonthViewEvent

fun MonthViewEvent.shouldStrikeThrough() = title.startsWith(COMPLETION_PREFIX) || isTaskCompleted || isAttendeeInviteDeclined || isEventCanceled
