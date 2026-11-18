package net.ankio.ledger.core

object ShakeSampling {
    const val PERIOD_US = 40_000

    fun allowsForeground(paymentAppsOnly: Boolean, packageName: String?): Boolean =
        !paymentAppsOnly || packageName != null && PaymentApps.supported(packageName)
}
