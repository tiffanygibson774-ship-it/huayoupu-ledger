package net.ankio.ledger

import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.ankio.ledger.core.Capture
import net.ankio.ledger.core.Direction
import net.ankio.ledger.core.ParsedBill
import net.ankio.ledger.core.RecordState
import net.ankio.ledger.core.Source
import net.ankio.ledger.data.LedgerRecord
import net.ankio.ledger.ui.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebViewBackgroundUiTest {
    @Test fun idleUiIsReleasedAndReturnsWithDraftAndFreshRecords() {
        val app = ApplicationProvider.getApplicationContext<LedgerApp>()
        var recordId: Long? = null
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val web = WebUiProbe(scenario)
                web.ready()
                web.run("""
                    newDraft(); go('import');
                    document.querySelector('#amount').value = '8.08';
                    document.querySelector('#note').value = 'background draft';
                    document.querySelector('#note').dispatchEvent(new Event('input', {bubbles:true}));
                """.trimIndent())
                lateinit var activity: MainActivity
                lateinit var original: android.webkit.WebView
                scenario.onActivity { activity = it; original = it.webView }
                assertFalse(activityOnMain { activity.releaseIdleWebView() })

                scenario.moveToState(Lifecycle.State.CREATED)
                val deadline = SystemClock.elapsedRealtime() + 5_000
                // Exercise the scheduled release without waiting for its 30-second grace period.
                var released: Boolean
                do {
                    released = activityOnMain { activity.releaseIdleWebView() }
                    if (!released) SystemClock.sleep(20)
                } while (!released && SystemClock.elapsedRealtime() < deadline)
                assertTrue("The stopped Activity should release its WebView", released)
                assertTrue(activityOnMain { original.parent == null })

                val now = System.currentTimeMillis()
                val saved = app.store.saveEdited(LedgerRecord(
                    capture = Capture(Source.MANUAL, "manual", "", now),
                    bill = ParsedBill(2250, Direction.EXPENSE, "Background purchase", "餐饮", now, null, true, "", true),
                    state = RecordState.POSTED,
                ))
                recordId = saved.id
                app.changed()
                scenario.moveToState(Lifecycle.State.RESUMED)
                scenario.onActivity { assertNotSame(original, it.webView) }
                web.await("document.querySelector('#import').classList.contains('mobile-active') && " +
                    "document.querySelector('#amount').value === '8.08' && " +
                    "document.querySelector('#note').value === 'background draft'")
                web.run("go('ledger')")
                web.await("Boolean(document.querySelector('[data-record=\"${saved.id}\"]'))")
            }
        } finally {
            recordId?.let { app.store.delete(it); app.changed() }
        }
    }

    private fun activityOnMain(action: () -> Boolean): Boolean {
        var result = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = action() }
        return result
    }
}
