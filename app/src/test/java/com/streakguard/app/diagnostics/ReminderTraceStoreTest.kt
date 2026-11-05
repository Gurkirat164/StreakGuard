package com.streakguard.app.diagnostics

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReminderTraceStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun missedReceiverSurvivesRestartAndRearming() {
        val directory = temporary.newFolder()
        val time = System.currentTimeMillis()
        ReminderTraceStore(directory) { time }.apply {
            record("first", "schedule_requested", mapOf("slot" to "1010", "at_ms" to (time - 120_000).toString()))
            record("first", "scheduled")
        }
        val reopened = ReminderTraceStore(directory) { time }
        assertEquals("scheduled_no_receiver_evidence_cause_unknown", ReminderTraceStore.finding(reopened.snapshots().single(), time))
        reopened.cancelSlot(1010, "app_open")
        reopened.record("tomorrow", "schedule_requested", mapOf("slot" to "1010", "at_ms" to (time + 86_400_000).toString()))
        assertEquals("overdue_no_receiver_before_app_cancel", ReminderTraceStore.finding(reopened.snapshots().first(), time))
        assertEquals(2, reopened.snapshots().size)
    }

    @Test fun receivingAndEnqueueingOutOfOrderDoesNotLoseWorkerEvidence() {
        val time = System.currentTimeMillis()
        val store = ReminderTraceStore(temporary.newFolder()) { time }
        store.record("first", "schedule_requested", mapOf("slot" to "1010", "at_ms" to (time - 120_000).toString()))
        store.record("first", "receiver_entered")
        store.record("first", "worker_started")
        store.record("first", "enqueue_requested")
        store.cancelSlot(1010, "receiver_rearm")
        val row = store.snapshots().single()
        assertNull(row["cancelled"])
        assertEquals("worker_started_no_terminal_evidence", ReminderTraceStore.finding(row, time))
    }

    @Test fun prematureCancellationAndScheduleFailureHaveDistinctFindings() {
        val time = System.currentTimeMillis()
        val store = ReminderTraceStore(temporary.newFolder()) { time }
        store.record("cancel", "schedule_requested", mapOf("slot" to "1010", "at_ms" to (time + 100_000).toString()))
        store.cancelSlot(1010, "settings_changed")
        assertEquals("cancelled_by_app", ReminderTraceStore.finding(store.snapshots().single(), time))
        store.record("failed", "schedule_requested")
        store.record("failed", "schedule_failed")
        assertEquals("schedule_failed", ReminderTraceStore.finding(store.snapshots().first { it["trace_id"] == "failed" }, time))
    }

    @Test fun postedAndBlockedRemainVisibleAfterWorkerFinishes() {
        val time = System.currentTimeMillis()
        val store = ReminderTraceStore(temporary.newFolder()) { time }
        store.record("blocked", "schedule_requested")
        store.record("blocked", "notification_blocked", mapOf("reason" to "channel_disabled"))
        store.record("blocked", "worker_finished")
        store.record("posted", "schedule_requested")
        store.record("posted", "notification_posted", mapOf("kind" to "reminder_tier_0"))
        store.record("posted", "worker_finished")
        assertEquals("notification_blocked", ReminderTraceStore.finding(store.snapshots().first { it["trace_id"] == "blocked" }, time))
        assertEquals("posting_accepted_display_unconfirmed", ReminderTraceStore.finding(store.snapshots().first { it["trace_id"] == "posted" }, time))
    }

    @Test fun unknownIdCannotCreateOccurrenceAndClearRemovesLedger() {
        val store = ReminderTraceStore(temporary.newFolder())
        store.record("legacy", "receiver_entered")
        assertTrue(store.snapshots().isEmpty())
        store.record("new", "schedule_requested")
        store.clear()
        assertTrue(store.snapshots().isEmpty())
    }

    @Test fun ledgerEvictsBeyondCapacityAndExpiresAfterSevenDays() {
        val directory = temporary.newFolder()
        val time = System.currentTimeMillis()
        val store = ReminderTraceStore(directory, maxEntries = 3) { time }
        repeat(5) { store.record("trace-$it", "schedule_requested") }
        assertEquals(3, store.snapshots().size)
        assertTrue(ReminderTraceStore(directory) { time + 8L * 86_400_000 }.snapshots().isEmpty())
    }

    @Test fun privateFieldsCannotEnterLedgerOrEventsThroughAllowlist() {
        val safe = DiagnosticFields.safeValues(mapOf("username" to "private", "signature" to "private",
            "trace_id" to "random-id", "reason" to "permission_disabled"))
        assertEquals(setOf("trace_id", "reason"), safe.keys)
        assertFalse(DiagnosticFields.encode(safe).contains("private"))
    }

    @Test fun corruptOccurrenceIsReportedWithoutHidingOtherReminders() {
        val directory = temporary.newFolder()
        val store = ReminderTraceStore(directory)
        store.record("valid", "schedule_requested")
        java.io.File(directory, "broken.trace").writeText("bad=\\uXXXX")
        val rows = store.snapshots()
        assertEquals(2, rows.size)
        assertEquals("diagnostic_ledger_unreadable", ReminderTraceStore.finding(rows.first { it["trace_id"] == "broken" }, 0))
    }
}
