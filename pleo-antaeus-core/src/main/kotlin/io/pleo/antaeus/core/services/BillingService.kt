package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import mu.KotlinLogging
import java.time.Instant

class BillingService(
    private val paymentProvider: PaymentProvider,
    private val dal: AntaeusDal,
    private val emailService: EmailService
) {

    private val logger = KotlinLogging.logger { }

    fun processPendingInvoices() {
        val start = Instant.now()
        dal.fetchInvoicesByStatus(InvoiceStatus.PENDING)
            .forEach { wrap(it, ::processInvoice) }
        sendBillingReport(from = start, to = Instant.now())
    }

    fun closePendingInvoices() {
        val start = Instant.now()
        dal.fetchInvoicesByStatus(InvoiceStatus.PENDING)
            .forEach { wrap(it, ::closeInvoice) }
        sendBillingReport(from = start, to = Instant.now())
    }

    fun sendBillingReport(from: Instant, to: Instant) {
        val billingResults = dal.countBillingResults(from, to)
            .associate { it.result to it.count }
        emailService.sendBillingResults(billingResults)
    }

    private fun wrap(invoice: Invoice, process: (Invoice) -> Unit) {
        try {
            process(invoice)
        } catch (e: Exception) {
            logger.error("Error processing invoice ${invoice.id}", e)
            handleUnknownException(invoice, e)
        }
    }

    private fun processInvoice(invoice: Invoice) {
        var successful: Boolean? = null
        try {
            successful = paymentProvider.charge(invoice)
        } catch (e: NetworkException) {
            handleNetworkException(invoice)
        } catch (e: CustomerNotFoundException) {
            handleCustomerNotFoundException(invoice)
        } catch (e: CurrencyMismatchException) {
            handleCurrencyMismatchException(invoice)
        }
        when (successful) {
            true -> handlePaymentSuccessful(invoice)
            false -> handlePaymentUnsuccessful(invoice)
            null -> {} // already handled above
        }
    }

    private fun handlePaymentSuccessful(invoice: Invoice) {
        dal.updateInvoiceStatus(invoice.id, InvoiceStatus.PAID)
        dal.createBillingLog(invoice.id, "Payment Successful")
    }

    private fun handlePaymentUnsuccessful(invoice: Invoice) {
        // do not change status, retry later
        val emailSent = emailService.sendPaymentUnsuccessfulNotice(invoice.customerId)
        val comment = if (emailSent) "Email sent" else "Email not send"
        dal.createBillingLog(invoice.id, "Payment Unsuccessful", comment)
    }

    private fun handleNetworkException(invoice: Invoice) {
        // do not change status, retry later
        dal.createBillingLog(invoice.id, NetworkException::class.simpleName)
    }

    private fun handleCustomerNotFoundException(invoice: Invoice) {
        dal.updateInvoiceStatus(invoice.id, InvoiceStatus.FAILED)
        val customer = dal.fetchCustomer(invoice.customerId)
        val comment = if (customer != null) "Customer found in database" else "Customer not found in database"
        dal.createBillingLog(invoice.id, CustomerNotFoundException::class.simpleName, comment)
    }

    private fun handleCurrencyMismatchException(invoice: Invoice) {
        dal.updateInvoiceStatus(invoice.id, InvoiceStatus.FAILED)
        val customer = dal.fetchCustomer(invoice.customerId)
        val comment = if (customer != null) {
            "Customer currency in database is ${customer.currency}"
        } else {
            "Customer not found in database"
        }
        dal.createBillingLog(invoice.id, CurrencyMismatchException::class.simpleName, comment)
    }

    private fun handleUnknownException(invoice: Invoice, e: Exception) {
        dal.updateInvoiceStatus(invoice.id, InvoiceStatus.FAILED)
        dal.createBillingLog(invoice.id, e::class.simpleName, e.message)
    }

    // should change status in a batch update, but oh well
    private fun closeInvoice(invoice: Invoice) {
        dal.updateInvoiceStatus(invoice.id, InvoiceStatus.FAILED)
        dal.createBillingLog(invoice.id, "Overdue")
    }
}
