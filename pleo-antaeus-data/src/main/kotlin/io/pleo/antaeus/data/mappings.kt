/*
    Defines mappings between database rows and Kotlin objects.
    To be used by `AntaeusDal`.
 */

package io.pleo.antaeus.data

import io.pleo.antaeus.models.*
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.count
import java.time.Instant

fun ResultRow.toInvoice(): Invoice = Invoice(
    id = this[InvoiceTable.id],
    amount = Money(
        value = this[InvoiceTable.value],
        currency = Currency.valueOf(this[InvoiceTable.currency])
    ),
    status = InvoiceStatus.valueOf(this[InvoiceTable.status]),
    customerId = this[InvoiceTable.customerId]
)

fun ResultRow.toCustomer(): Customer = Customer(
    id = this[CustomerTable.id],
    currency = Currency.valueOf(this[CustomerTable.currency])
)

fun ResultRow.toBillingLog(): BillingLog = BillingLog(
    id = this[BillingLogTable.id],
    invoiceId = this[BillingLogTable.invoiceId],
    result = this[BillingLogTable.result],
    comment = this[BillingLogTable.comment],
    timestamp = this[BillingLogTable.timestamp].let { Instant.ofEpochMilli(it.millis) }
)

fun ResultRow.toBillingLogCount(): BillingLogCount = BillingLogCount(
    result = this[BillingLogTable.result],
    count = this[BillingLogTable.id.count()]
)
