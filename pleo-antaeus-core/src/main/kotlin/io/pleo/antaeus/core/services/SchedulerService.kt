package io.pleo.antaeus.core.services

import com.github.kagkarlsson.scheduler.Scheduler
import com.github.kagkarlsson.scheduler.task.helper.Tasks
import com.github.kagkarlsson.scheduler.task.schedule.CronSchedule
import javax.sql.DataSource

class SchedulerService(dataSource: DataSource, billingService: BillingService) {

    private val processInvoicesTask = Tasks.recurring("billing", CronSchedule("0 0 0 1,2,3 * ?"))
        .execute { _, _ -> billingService.processPendingInvoices() }

    private val closeInvoicesTask = Tasks.recurring("overdue", CronSchedule("0 0 8 3 * ?"))
        .execute { _, _ -> billingService.closePendingInvoices() }

    private val testTask = Tasks.recurring("test", CronSchedule("*/10 * * * * ?"))
        .execute { _, _ -> println("test") }

    private val scheduler = Scheduler
        .create(dataSource)
        .registerShutdownHook()
        .startTasks(processInvoicesTask, closeInvoicesTask, testTask)
        .build()

    fun start() {
        scheduler.start()
    }

    fun stop() {
        scheduler.stop()
    }
}