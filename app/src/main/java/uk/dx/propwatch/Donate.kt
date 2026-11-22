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

    /** A respectfully cheeky line for each amount on the slider; every amount has its own. */
    fun quip(pounds: Int): String = QUIPS[pounds.coerceIn(MIN, MAX) - MIN]

    private val QUIPS = listOf(
        "One proper cuppa. Cheers, OM!",                                   // £1
        "A coffee and a biscuit. Very civilised.",                         // £2
        "A coffee to go and a spare fuse for the rig.",                     // £3
        "A flat white and a fresh roll of solder. Lovely.",                // £4
        "That's the morning brew sorted for the week.",                    // £5
        "A round of teas for the whole radio club.",                       // £6
        "Seven coffees: one for every night of a DXpedition week.",         // £7
        "Steady on — that's a new patch lead and a latte!",                // £8
        "Nine coffees? You're keeping the valves warm all month.",          // £9
        "A tenner?! I'll log your name in gold ink. 73 and thank you!",    // £10
    )

    fun cups(pounds: Int) = if (pounds == 1) "ONE COFFEE" else "${words[pounds]} COFFEES"

    private val words = listOf("", "ONE", "TWO", "THREE", "FOUR", "FIVE", "SIX", "SEVEN", "EIGHT", "NINE", "TEN")
}
