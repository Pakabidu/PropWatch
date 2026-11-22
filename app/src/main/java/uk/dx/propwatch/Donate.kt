package uk.dx.propwatch

import android.net.Uri

/** PayPal donation link. Voluntary: every feature of the app stays free. */
object Donate {
    private const val PAYPAL_ACCOUNT = "pakabidu@gmail.com"
    const val MIN = 1
    const val MAX = 10

    /** PayPal's standard donation page, prefilled with the amount in pounds. */
    fun url(pounds: Int): Uri = Uri.parse("https://www.paypal.com/cgi-bin/webscr").buildUpon()
        .appendQueryParameter("cmd", "_donations")
        .appendQueryParameter("business", PAYPAL_ACCOUNT)
        .appendQueryParameter("currency_code", "GBP")
        .appendQueryParameter("amount", "$pounds.00")
        .appendQueryParameter("item_name", "PropWatch coffee fund")
        .appendQueryParameter("no_shipping", "1")
        .build()

    /** A respectfully cheeky line for each amount on the slider. */
    fun quip(pounds: Int): String = when (pounds) {
        1 -> "One proper cuppa. Cheers, OM!"
        2 -> "A coffee and a biscuit. Very civilised."
        3 -> "Coffee, biscuit, and enough left for a solder tip."
        in 4..5 -> "That's the morning brew sorted for the week."
        in 6..7 -> "A round of teas for the whole shack."
        in 8..9 -> "Steady on — you'll spoil me. Thank you!"
        else -> "A tenner?! I'll log your name in gold ink. 73 and thank you!"
    }

    fun cups(pounds: Int) = if (pounds == 1) "ONE COFFEE" else "${words[pounds]} COFFEES"

    private val words = listOf("", "ONE", "TWO", "THREE", "FOUR", "FIVE", "SIX", "SEVEN", "EIGHT", "NINE", "TEN")
}
