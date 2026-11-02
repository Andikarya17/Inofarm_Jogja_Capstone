package com.inofarm.jogja

import android.app.job.JobInfo
import android.app.job.JobScheduler
import androidx.test.platform.app.InstrumentationRegistry
import com.inofarm.jogja.core.sync.FinanceSyncSchedule
import org.junit.Assert.*
import org.junit.Test

class FinanceSyncScheduleTest {
    @Test fun schedulerIsPersistentNetworkBoundAndContainsNoAccountCredentials() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(context.packageName.endsWith(".finance"))
        val info=FinanceSyncSchedule.info(context)
        assertTrue(info.isPeriodic);assertTrue(info.isPersisted);assertEquals(JobInfo.NETWORK_TYPE_ANY,info.networkType)
        assertTrue(info.extras.isEmpty);assertTrue(info.transientExtras.isEmpty)
        assertTrue(FinanceSyncSchedule.ensure(context));assertTrue(FinanceSyncSchedule.ensure(context))
        val scheduler=context.getSystemService(JobScheduler::class.java)
        assertEquals(1,scheduler.allPendingJobs.count {it.id==FinanceSyncSchedule.JOB_ID})
        scheduler.cancel(FinanceSyncSchedule.JOB_ID)
    }
}
