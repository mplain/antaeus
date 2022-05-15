/*
    Defines database tables and their schemas.
    To be used by `AntaeusDal`.
 */

package io.pleo.antaeus.data

import org.jetbrains.exposed.sql.CurrentDateTime
import org.jetbrains.exposed.sql.Table

object InvoiceTable : Table() {
    val id = integer("id").autoIncrement().primaryKey()
    val currency = varchar("currency", 3)
    val value = decimal("value", 1000, 2)
    val customerId = reference("customer_id", CustomerTable.id)
    val status = text("status")
}

object CustomerTable : Table() {
    val id = integer("id").autoIncrement().primaryKey()
    val currency = varchar("currency", 3)
}

object BillingLogTable : Table() {
    val id = integer("id").autoIncrement().primaryKey()
    val invoiceId = reference("invoice_id", InvoiceTable.id)
    val result = text("result")
    val comment = text("comment").nullable()
    val timestamp = datetime("timestamp").defaultExpression(CurrentDateTime())
}

object ScheduledTasksTable : Table("scheduled_tasks") {
    val taskName = text("task_name").primaryKey()
    val taskInstance = text("task_instance").primaryKey()
    val taskData = blob("task_data").nullable()
    val executionTime = datetime("execution_time")
    val picked = bool("picked")
    val pickedBy = text("picked_by").nullable()
    val lastSuccess = datetime("last_success").nullable()
    val lastFailure = datetime("last_failure").nullable()
    val consecutiveFailures = integer("consecutive_failures").nullable()
    val lastHeartbeat = datetime("last_heartbeat").nullable()
    val version = long("version")
}
