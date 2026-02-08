package org.fossify.calendar.extensions

import org.fossify.calendar.helpers.COMPLETION_PREFIX
import org.fossify.calendar.models.ListEvent

fun ListEvent.shouldStrikeThrough() = title.startsWith(COMPLETION_PREFIX) || isTaskCompleted || isAttendeeInviteDeclined || isEventCanceled
