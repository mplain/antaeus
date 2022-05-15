/*
    Implements the data access layer (DAL).
    The data access layer generates and executes requests to the database.

    See the `mappings` module for the conversions between database rows and Kotlin objects.
 */

package io.pleo.antaeus.data

import io.pleo.antaeus.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

class AntaeusDal(private val db: Database) {
    fun fetchInvoice(id: Int): Invoice? {
        // transaction(db) runs the internal query as a new database transaction.
        return transaction(db) {
            // Returns the first invoice with matching id.
            InvoiceTable
                .select { InvoiceTable.id.eq(id) }
                .firstOrNull()
                ?.toInvoice()
        }
    }

    // shouldn't expose a query like that, might be millions of entries, should always use some filter
    fun fetchInvoices(): List<Invoice> {
        return transaction(db) {
            InvoiceTable
                .selectAll()
                .map { it.toInvoice() }
        }
    }

    fun fetchInvoicesByStatus(status: InvoiceStatus): List<Invoice> =
        transaction(db) {
            InvoiceTable
                .select { InvoiceTable.status.eq(status.name) }
                .map { it.toInvoice() }
        }

    fun createInvoice(amount: Money, customer: Customer, status: InvoiceStatus = InvoiceStatus.PENDING): Invoice? {
        val id = transaction(db) {
            // Insert the invoice and returns its new id.
            InvoiceTable
                .insert {
                    it[this.value] = amount.value
                    it[this.currency] = amount.currency.toString()
                    it[this.status] = status.toString()
                    it[this.customerId] = customer.id
                } get InvoiceTable.id
        }

        return fetchInvoice(id)
    }

    fun updateInvoiceStatus(id: Int, status: InvoiceStatus) {
        transaction(db) {
            InvoiceTable
                .update({ InvoiceTable.id eq id }) {
                    it[this.status] = status.name
                }
        }
    }

    fun fetchCustomer(id: Int): Customer? {
        return transaction(db) {
            CustomerTable
                .select { CustomerTable.id.eq(id) }
                .firstOrNull()
                ?.toCustomer()
        }
    }

    fun fetchCustomers(): List<Customer> {
        return transaction(db) {
            CustomerTable
                .selectAll()
                .map { it.toCustomer() }
        }
    }

    fun createCustomer(currency: Currency): Customer? {
        val id = transaction(db) {
            // Insert the customer and return its new id.
            CustomerTable.insert {
                it[this.currency] = currency.toString()
            } get CustomerTable.id
        }

        return fetchCustomer(id)
    }

    // would rather separate this DAL class into three DAO classes, but let's keep the existing structure
    fun createBillingLog(invoiceId: Int, result: String?, comment: String? = null) {
        transaction(db) {
            BillingLogTable.insert {
                it[this.invoiceId] = invoiceId
                it[this.result] = result ?: "Unknown"
                it[this.comment] = comment
            }
        }
    }

    fun countBillingResults(from: Instant, to: Instant): List<BillingLogCount> =
        transaction(db) {
            BillingLogTable
                .slice(BillingLogTable.result, BillingLogTable.id.count())
                .select { (BillingLogTable.timestamp greaterEq from) and (BillingLogTable.timestamp lessEq to) }
                .groupBy(BillingLogTable.result)
                .map { it.toBillingLogCount() }
        }
}
