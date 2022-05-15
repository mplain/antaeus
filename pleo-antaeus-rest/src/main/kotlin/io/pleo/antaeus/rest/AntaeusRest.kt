/*
    Configures the rest app along with basic exception handling and URL endpoints.
 */

package io.pleo.antaeus.rest

import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.*
import io.javalin.http.Context
import io.pleo.antaeus.core.exceptions.EntityNotFoundException
import io.pleo.antaeus.core.services.BillingService
import io.pleo.antaeus.core.services.CustomerService
import io.pleo.antaeus.core.services.InvoiceService
import io.pleo.antaeus.core.services.SchedulerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.eclipse.jetty.http.HttpStatus
import java.time.Instant
import java.time.temporal.ChronoUnit

private val logger = KotlinLogging.logger {}
private val thisFile: () -> Unit = {}

class AntaeusRest(
    private val invoiceService: InvoiceService,
    private val customerService: CustomerService,
    private val billingService: BillingService,
    private val schedulerService: SchedulerService
) : Runnable {

    override fun run() {
        app.start(7000)
        schedulerService.start()
    }

    // Set up Javalin rest app
    private val app = Javalin
        .create()
        .apply {
            // InvoiceNotFoundException: return 404 HTTP status code
            exception(EntityNotFoundException::class.java) { _, ctx ->
                ctx.status(404)
            }
            // Unexpected exception: return HTTP 500
            exception(Exception::class.java) { e, _ ->
                logger.error(e) { "Internal server error" }
            }
            // On 404: return message
            error(404) { ctx -> ctx.json("not found") }
        }

    init {
        // Set up URL endpoints for the rest app
        app.routes {
            get("/") {
                it.result("Welcome to Antaeus! see AntaeusRest class for routes")
            }
            path("rest") {
                // Route to check whether the app is running
                // URL: /rest/health
                get("health") {
                    it.json("ok")
                }

                // V1
                path("v1") {
                    path("invoices") {
                        // URL: /rest/v1/invoices
                        get {
                            it.json(invoiceService.fetchAll())
                        }

                        // URL: /rest/v1/invoices/{:id}
                        get(":id") {
                            it.json(invoiceService.fetch(it.pathParam("id").toInt()))
                        }
                    }

                    path("customers") {
                        // URL: /rest/v1/customers
                        get {
                            it.json(customerService.fetchAll())
                        }

                        // URL: /rest/v1/customers/{:id}
                        get(":id") {
                            it.json(customerService.fetch(it.pathParam("id").toInt()))
                        }
                    }

                    path("billing") {
                        // URL: /rest/v1/billing/process
                        post("process") {
                            it.async { billingService.processPendingInvoices() }
                        }

                        // URL: /rest/v1/billing/close
                        post("close") {
                            it.async { billingService.closePendingInvoices() }
                        }

                        // URL: /rest/v1/billing/report
                        post("report") {
                            val from = it.queryParam("from")?.let(Instant::parse)
                                ?: Instant.now().minus(1, ChronoUnit.DAYS)
                            val to = it.queryParam("to")?.let(Instant::parse)
                                ?: Instant.now()
                            it.async { billingService.sendBillingReport(from = from, to = to) }
                        }
                    }

                    path("scheduler") {
                        // URL: /rest/v1/scheduler/start
                        post("start") {
                            schedulerService.start()
                        }

                        // URL: /rest/v1/scheduler/stop
                        post("stop") {
                            schedulerService.stop()
                        }
                    }
                }
            }
        }
    }

    private fun Context.async(job: () -> Unit) {
        CoroutineScope(Dispatchers.Default).launch { job() }
        status(HttpStatus.ACCEPTED_202)
    }
}
