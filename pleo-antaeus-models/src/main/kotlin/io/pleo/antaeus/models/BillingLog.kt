package io.pleo.antaeus.models

import java.time.Instant

data class BillingLog(
    val id: Int,
    val invoiceId: Int,
    val result: String,
    val comment: String? = null,
    val timestamp: Instant
)
