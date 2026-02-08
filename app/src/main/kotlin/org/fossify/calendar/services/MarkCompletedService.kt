package org.fossify.calendar.services

import android.app.IntentService
import android.content.Intent
import org.fossify.calendar.extensions.*
import org.fossify.calendar.helpers.ACTION_MARK_COMPLETED
import org.fossify.calendar.helpers.COMPLETION_PREFIX
import org.fossify.calendar.helpers.EVENT_ID
import org.fossify.calendar.helpers.EVENT_OCCURRENCE_TS

class MarkCompletedService : IntentService("MarkCompleted") {

    @Deprecated("Deprecated in Java")
    override fun onHandleIntent(intent: Intent?) {
        if (intent != null && intent.action == ACTION_MARK_COMPLETED) {
            val eventId = intent.getLongExtra(EVENT_ID, 0L)
            val event = eventsDB.getEventOrTaskWithId(eventId)
            if (event != null) {
                val occurrenceTS = intent.getLongExtra(EVENT_OCCURRENCE_TS, 0L)
                if (event.isTask()) {
                    if (occurrenceTS != 0L) {
                        event.startTS = occurrenceTS
                        event.endTS = occurrenceTS
                    }
                    updateTaskCompletion(event, completed = true)
                } else {
                    if (!event.title.startsWith(COMPLETION_PREFIX)) {
                        event.title = "${COMPLETION_PREFIX}${event.title}"
                    }

                    if (event.repeatInterval != 0 && occurrenceTS != 0L) {
                        eventsHelper.editSelectedOccurrence(event, occurrenceTS, false) {}
                    } else {
                        eventsHelper.updateEvent(event, updateAtCalDAV = true, showToasts = false)
                    }
                    cancelNotification(event.id!!)
                }
                updateWidgets()
            }
        }
    }
}
