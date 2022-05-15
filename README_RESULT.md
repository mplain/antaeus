- I will add a scheduled job inside the javalin app, as a separate service, and implement ShedLock to ensure that only
  one instance of the app runs the job
- (l will assume the REST endpoints are for manual processing of customers and invoices, not for our scheduled job)
- The job will run three times (on the 1st, 2nd, and 3rd of every month), in order to retry processing some failed
  invoices
- If the payment is processed successfully, I will update the invoice status to Paid
- If the payment is processed unsuccessfully (not enough money), or there's a Network error, or an infrastructure
  problem (OutOfMemory, app reboot), I will not change the invoice status for the first two runs of the job
- If there's any other exception, or a non-critical exception during the third run of the job, then I will update the
  invoice status to Error
- If there's a CustomerNotFoundException or a CurrencyMismatchException, I will check the state of data in the database
- In any case, I will save the result of processing the invoice into the database (new table). It's someone else's
  responsibility to decide whether to send an email to the client, cancel the client's subscription, or inform the
  employees about a problem (can implement, but seems out of scope of this task)

--- final notes
+ send email to client if payment unsuccessful
+ send report to employee
