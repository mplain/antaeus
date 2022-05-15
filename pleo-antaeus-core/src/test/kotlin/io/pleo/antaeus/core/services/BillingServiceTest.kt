package io.pleo.antaeus.core.services

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.matchers.maps.shouldContainExactly
import io.mockk.*
import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.*
import java.math.BigDecimal

class BillingServiceTest : FeatureSpec() {

    private val paymentProvider = mockk<PaymentProvider>()
    private val dal = mockk<AntaeusDal>(relaxed = true)
    private val emailService = mockk<EmailService>()

    private val billingService = BillingService(paymentProvider, dal, emailService)
    private val billingLogs = mutableListOf<String>()
    private val billingResultsSlot = slot<Map<String, Int>>()

    override suspend fun beforeEach(testCase: TestCase) {
        every { paymentProvider.charge(any()) } answers { mockCharge(firstArg()) }
        every { dal.fetchInvoicesByStatus(InvoiceStatus.PENDING) } returns invoices
        every { dal.fetchCustomer(any()) } answers { Customer(firstArg(), Currency.EUR) }
        every { dal.fetchCustomer(8) } returns null
        every { dal.fetchCustomer(10) } returns Customer(10, Currency.USD)
        every { dal.createBillingLog(any(), any(), any()) } answers { billingLogs.add(secondArg()) }
        every { dal.countBillingResults(any(), any()) } answers {
            billingLogs.groupBy { it }.map { BillingLogCount(it.key, it.value.count()) }
        }
        every { emailService.sendPaymentUnsuccessfulNotice(3) } returns true
        every { emailService.sendPaymentUnsuccessfulNotice(4) } returns false
        every { emailService.sendBillingResults(capture(billingResultsSlot)) } just runs
    }

    override suspend fun afterEach(testCase: TestCase, result: TestResult) {
        clearAllMocks()
        billingLogs.clear()
        billingResultsSlot.clear()
    }

    init {
        feature("test billing service") {
            scenario("process pending invoices") {
                billingService.processPendingInvoices()

                verifyAll {
                    dal.fetchInvoicesByStatus(InvoiceStatus.PENDING)

                    paymentProvider.charge(match { it.id == 1 })
                    dal.updateInvoiceStatus(1, InvoiceStatus.PAID)
                    dal.createBillingLog(1, "Payment Successful")

                    paymentProvider.charge(match { it.id == 2 })
                    dal.updateInvoiceStatus(2, InvoiceStatus.PAID)
                    dal.createBillingLog(2, "Payment Successful")

                    paymentProvider.charge(match { it.id == 3 })
                    emailService.sendPaymentUnsuccessfulNotice(3)
                    dal.createBillingLog(3, "Payment Unsuccessful", "Email sent")

                    paymentProvider.charge(match { it.id == 4 })
                    emailService.sendPaymentUnsuccessfulNotice(4)
                    dal.createBillingLog(4, "Payment Unsuccessful", "Email not send")

                    paymentProvider.charge(match { it.id == 5 })
                    dal.createBillingLog(5, "NetworkException")

                    paymentProvider.charge(match { it.id == 6 })
                    dal.createBillingLog(6, "NetworkException")

                    paymentProvider.charge(match { it.id == 7 })
                    dal.updateInvoiceStatus(7, InvoiceStatus.FAILED)
                    dal.fetchCustomer(7)
                    dal.createBillingLog(7, "CustomerNotFoundException", "Customer found in database")

                    paymentProvider.charge(match { it.id == 8 })
                    dal.updateInvoiceStatus(8, InvoiceStatus.FAILED)
                    dal.fetchCustomer(8)
                    dal.createBillingLog(8, "CustomerNotFoundException", "Customer not found in database")

                    paymentProvider.charge(match { it.id == 9 })
                    dal.updateInvoiceStatus(9, InvoiceStatus.FAILED)
                    dal.fetchCustomer(9)
                    dal.createBillingLog(9, "CurrencyMismatchException", "Customer currency in database is EUR")

                    paymentProvider.charge(match { it.id == 10 })
                    dal.updateInvoiceStatus(10, InvoiceStatus.FAILED)
                    dal.fetchCustomer(10)
                    dal.createBillingLog(10, "CurrencyMismatchException", "Customer currency in database is USD")

                    paymentProvider.charge(match { it.id == 11 })
                    dal.updateInvoiceStatus(11, InvoiceStatus.FAILED)
                    dal.createBillingLog(11, "IllegalStateException", "Unknown error")

                    dal.countBillingResults(any(), any())
                    emailService.sendBillingResults(any())
                }
                billingResultsSlot.captured shouldContainExactly mapOf(
                    "Payment Successful" to 2,
                    "Payment Unsuccessful" to 2,
                    "NetworkException" to 2,
                    "CustomerNotFoundException" to 2,
                    "CurrencyMismatchException" to 2,
                    "IllegalStateException" to 1
                )
            }
            scenario("close pending invoices") {
                billingService.closePendingInvoices()

                verify(exactly = 1) {
                    dal.fetchInvoicesByStatus(InvoiceStatus.PENDING)
                    dal.countBillingResults(any(), any())
                    emailService.sendBillingResults(any())
                }
                verify(exactly = 11) {
                    dal.updateInvoiceStatus(any(), InvoiceStatus.FAILED)
                    dal.createBillingLog(any(), "Overdue")
                }
                billingResultsSlot.captured shouldContainExactly mapOf("Overdue" to 11)
            }
        }
    }

    private fun mockCharge(invoice: Invoice): Boolean = when (invoice.id) {
        1, 2 -> true
        3, 4 -> false
        5, 6 -> throw NetworkException()
        7, 8 -> throw CustomerNotFoundException(invoice.customerId)
        9, 10 -> throw CurrencyMismatchException(invoice.id, invoice.customerId)
        else -> throw IllegalStateException("Unknown error")
    }

    private val invoices = (1..11).map { Invoice(it, it, Money(BigDecimal(it), Currency.EUR), InvoiceStatus.PENDING) }
}
