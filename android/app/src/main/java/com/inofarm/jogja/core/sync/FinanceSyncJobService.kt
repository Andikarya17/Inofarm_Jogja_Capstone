package com.inofarm.jogja.core.sync

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.inofarm.jogja.InofarmApplication
import kotlinx.coroutines.*

/** No credentials/payload in JobInfo. The durable outbox and current vault remain authoritative. */
object FinanceSyncSchedule {
    const val JOB_ID=1601
    fun info(context:Context)=JobInfo.Builder(JOB_ID,ComponentName(context,FinanceSyncJobService::class.java))
        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true).setPeriodic(15*60*1000L).build()
    fun ensure(context:Context):Boolean {
        return try {
            val scheduler=context.getSystemService(JobScheduler::class.java)
            scheduler.getPendingJob(JOB_ID)!=null || scheduler.schedule(info(context))==JobScheduler.RESULT_SUCCESS
        } catch(_:SecurityException) {false}
    }
}

class FinanceSyncJobService:JobService() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var active:JobParameters?=null
    private var running:Job?=null
    override fun onStartJob(params:JobParameters):Boolean {
        active=params
        running=scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val runtime=(application as InofarmApplication).finance
                    val session=runtime.vault.restore()
                    if(session!=null && !session.expired()) runtime.sync.run()
                }
            } catch(cancelled:CancellationException) {throw cancelled}
            catch(_:Exception) {Log.w("FinanceSyncJob","Sinkronisasi ditunda; draf dan antrean tetap disimpan.")}
            finally {if(active===params) {active=null;jobFinished(params,false)}}
        }
        return true
    }
    override fun onStopJob(params:JobParameters):Boolean {
        if(active===params) {active=null;running?.cancel();running=null}
        return true
    }
    override fun onDestroy() {scope.cancel();super.onDestroy()}
}
