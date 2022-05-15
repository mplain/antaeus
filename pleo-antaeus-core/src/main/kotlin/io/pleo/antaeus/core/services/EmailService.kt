package io.pleo.antaeus.core.services

import mu.KotlinLogging

class EmailService {

    private val logger = KotlinLogging.logger { }

    fun sendPaymentUnsuccessfulNotice(customerId: Int): Boolean =
        try {
            // get customer email by id
            // send an email informing that the customer account balance did not allow the charge
            true
        } catch (e: Exception) {
            logger.error("Error sending email to customer $customerId", e)
            false
        }

    fun sendBillingResults(billingResults: Map<String, Int>) {
        try {
            // send an email with the report to the employee responsible for overseeing the monthly billing process
        } catch (e: Exception) {
            logger.error("Error sending email with billing results", e)
        }
    }
}