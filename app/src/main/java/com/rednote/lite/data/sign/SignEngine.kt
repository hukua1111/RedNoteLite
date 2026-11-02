package com.rednote.lite.data.sign

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.content.Context
import android.webkit.CookieManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.coroutines.resume

/**
 * 小红书网页版签名引擎。
 *
 * 背景：www.xiaohongshu.com / edith.xiaohongshu.com 的绝大多数读接口要求
 * `X-s` 头中的 `x3` 字段来自站点自身的反爬 SDK `window.mnsv2`。
 * 这个 SDK 是混淆 VM（mns0301 字节码），无法脱离浏览器环境复刻，
 * 因此这里用一个隐藏的 WebView 加载真实网页，在页面内调用官方 `mnsv2` 生成签名。
 *
 * 流程：
 *  1. warmUp() —— 加载 https://www.xiaohongshu.com/explore ，等页面内 JS 注入 mnsv2。
 *  2. sign(path, body) —— 在页面内执行脚本，得到 {x-s, x-t, x-s-common}。
 */
class SignEngine(private val context: Context) {

    companion object {
        private const val TAG = "SignEngine"
        private const val HOME_URL = "https://www.xiaohongshu.com/explore"
        private const val SIGN_TIMEOUT_MS = 15_000L
        private const val READY_TIMEOUT_MS = 25_000L
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

        /**
         * 桌面版 UA：登录页用它加载 PC 版网页，避免被引导去下载 App，
         * 同时和接口签名里声明的 x2=PC / x1=xhs-pc-web 保持一致。
         */
        const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }

    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var webView: WebView? = null

    @Volatile
    private var pageReady = false

    private var readyDeferred = CompletableDeferred<Boolean>()

    /** 页面里的签名实现，直接复用站点自己的 seccore_signv2 逻辑。 */
    private val signScript = """
        (function(){
            try {
                if (typeof window.mnsv2 !== 'function') {
                    return JSON.stringify({error: 'no-mnsv2'});
                }
                var path = %s;
                var body = %s;
                var bodyStr = path + body;
                var u = %s(bodyStr);
                var p = %s(path);

                // ---- X-s ----
                var x3 = window.mnsv2(bodyStr, u, p);
                var xsObj = {x0:'4.3.9', x1:'xhs-pc-web', x2:'PC', x3: x3, x4: body, x5: u};
                var xs = 'XYS_' + %s(JSON.stringify(xsObj));

                // ---- X-S-Common ----
                var a1 = (%s);
                var common = {
                    s0: %d, s1: '', x0: '1', x1: '4.3.9', x2: 'PC',
                    x3: 'xhs-pc-web', x4: '6.7.1', x5: a1,
                    x6: '', x7: '', x8: '', x9: 0, x10: 0, x11: 'normal', x12: '0;'
                };
                var xsc = %s(JSON.stringify(common));
                return JSON.stringify({xs: xs, xt: String(Date.now()), common: xsc, x3: String(x3).slice(0, 24)});
            } catch (e) {
                return JSON.stringify({error: String(e && e.message || e)});
            }
        })()
    """.trimIndent()

    private val helpers = """
        var __b64c = (function(){
            var STD = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
            var XHS = 'ZmserbBoHQtNP+wOcza/LpngG8yJq42KWYj0DSfdikx3VT16IlUAFM97hECvuRX5';
            var map = {}; for (var i=0;i<64;i++) map[STD[i]] = XHS[i];
            return function(str){
                var b = btoa(unescape(encodeURIComponent(str))).replace(/=+$/,'');
                var out = '';
                for (var i=0;i<b.length;i++) out += (map[b[i]] || b[i]);
                return out;
            };
        })();
        var __md5 = (function(){
            // 轻量 md5（RFC1321），避免依赖站点内部实现
            function safeAdd(x,y){var l=(x&0xFFFF)+(y&0xFFFF);var m=(x>>16)+(y>>16)+(l>>16);return (m<<16)|(l&0xFFFF);}
            function rol(n,c){return (n<<c)|(n>>>(32-c));}
            function cmn(q,a,b,x,s,t){return safeAdd(rol(safeAdd(safeAdd(a,q),safeAdd(x,t)),s),b);}
            function ff(a,b,c,d,x,s,t){return cmn((b&c)|((~b)&d),a,b,x,s,t);}
            function gg(a,b,c,d,x,s,t){return cmn((b&d)|(c&(~d)),a,b,x,s,t);}
            function hh(a,b,c,d,x,s,t){return cmn(b^c^d,a,b,x,s,t);}
            function ii(a,b,c,d,x,s,t){return cmn(c^(b|(~d)),a,b,x,s,t);}
            function md5cycle(x,k){
                var a=x[0],b=x[1],c=x[2],d=x[3];
                a=ff(a,b,c,d,k[0],7,-680876936);d=ff(d,a,b,c,k[1],12,-389564586);
                c=ff(c,d,a,b,k[2],17,606105819);b=ff(b,c,d,a,k[3],22,-1044525330);
                a=ff(a,b,c,d,k[4],7,-176418897);d=ff(d,a,b,c,k[5],12,1200080426);
                c=ff(c,d,a,b,k[6],17,-1473231341);b=ff(b,c,d,a,k[7],22,-45705983);
                a=ff(a,b,c,d,k[8],7,1770035416);d=ff(d,a,b,c,k[9],12,-1958414417);
                c=ff(c,d,a,b,k[10],17,-42063);b=ff(b,c,d,a,k[11],22,-1990404162);
                a=ff(a,b,c,d,k[12],7,1804603682);d=ff(d,a,b,c,k[13],12,-40341101);
                c=ff(c,d,a,b,k[14],17,-1502002290);b=ff(b,c,d,a,k[15],22,1236535329);
                a=gg(a,b,c,d,k[1],5,-165796510);d=gg(d,a,b,c,k[6],9,-1069501632);
                c=gg(c,d,a,b,k[11],14,643717713);b=gg(b,c,d,a,k[0],20,-373897302);
                a=gg(a,b,c,d,k[5],5,-701558691);d=gg(d,a,b,c,k[10],9,38016083);
                c=gg(c,d,a,b,k[15],14,-660478335);b=gg(b,c,d,a,k[4],20,-405537848);
                a=gg(a,b,c,d,k[9],5,568446438);d=gg(d,a,b,c,k[14],9,-1019803690);
                c=gg(c,d,a,b,k[3],14,-187363961);b=gg(b,c,d,a,k[8],20,1163531501);
                a=gg(a,b,c,d,k[13],5,-1444681467);d=gg(d,a,b,c,k[2],9,-51403784);
                c=gg(c,d,a,b,k[7],14,1735328473);b=gg(b,c,d,a,k[12],20,-1926607734);
                a=hh(a,b,c,d,k[5],4,-378558);d=hh(d,a,b,c,k[8],11,-2022574463);
                c=hh(c,d,a,b,k[11],16,1839030562);b=hh(b,c,d,a,k[14],23,-35309556);
                a=hh(a,b,c,d,k[1],4,-1530992060);d=hh(d,a,b,c,k[4],11,1272893353);
                c=hh(c,d,a,b,k[7],16,-155497632);b=hh(b,c,d,a,k[10],23,-1094730640);
                a=hh(a,b,c,d,k[13],4,681279174);d=hh(d,a,b,c,k[0],11,-358537222);
                c=hh(c,d,a,b,k[3],16,-722521979);b=hh(b,c,d,a,k[6],23,76029189);
                a=hh(a,b,c,d,k[9],4,-640364487);d=hh(d,a,b,c,k[12],11,-421815835);
                c=hh(c,d,a,b,k[15],16,530742520);b=hh(b,c,d,a,k[2],23,-995338651);
                a=ii(a,b,c,d,k[0],6,-198630844);d=ii(d,a,b,c,k[7],10,1126891415);
                c=ii(c,d,a,b,k[14],15,-1416354905);b=ii(b,c,d,a,k[5],21,-57434055);
                a=ii(a,b,c,d,k[12],6,1700485571);d=ii(d,a,b,c,k[3],10,-1894986606);
                c=ii(c,d,a,b,k[10],15,-1051523);b=ii(b,c,d,a,k[1],21,-2054922799);
                a=ii(a,b,c,d,k[8],6,1873313359);d=ii(d,a,b,c,k[15],10,-30611744);
                c=ii(c,d,a,b,k[6],15,-1560198380);b=ii(b,c,d,a,k[13],21,1309151649);
                a=ii(a,b,c,d,k[4],6,-145523070);d=ii(d,a,b,c,k[11],10,-1120210379);
                c=ii(c,d,a,b,k[2],15,718787259);b=ii(b,c,d,a,k[9],21,-343485551);
                x[0]=safeAdd(a,x[0]);x[1]=safeAdd(b,x[1]);x[2]=safeAdd(c,x[2]);x[3]=safeAdd(d,x[3]);
            }
            function md5blk(s){var md5blks=[];for(var i=0;i<64;i+=4){md5blks[i>>2]=s.charCodeAt(i)+(s.charCodeAt(i+1)<<8)+(s.charCodeAt(i+2)<<16)+(s.charCodeAt(i+3)<<24);}return md5blks;}
            function md51(s){
                var n=s.length, state=[1732584193,-271733879,-1732584194,271733878], i;
                for(i=64;i<=n;i+=64){md5cycle(state,md5blk(s.substring(i-64,i)));}
                s=s.substring(i-64);
                var tail=[0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0];
                for(i=0;i<s.length;i++) tail[i>>2]|=s.charCodeAt(i)<<((i%4)<<3);
                tail[i>>2]|=0x80<<((i%4)<<3);
                if(i>55){md5cycle(state,tail);tail=[0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0];}
                tail[14]=n*8;
                md5cycle(state,tail);
                return state;
            }
            var hex='0123456789abcdef';
            function rhex(n){var s='',j;for(j=0;j<4;j++) s+=hex.charAt((n>>(j*8+4))&0x0F)+hex.charAt((n>>(j*8))&0x0F);return s;}
            function md5(s){
                s=unescape(encodeURIComponent(s));
                var x=md51(s), out='';
                for(var i=0;i<x.length;i++) out+=rhex(x[i]);
                return out;
            }
            return md5;
        })();
    """.trimIndent()

    private fun ensureWebView(): WebView {
        webView?.let { return it }
        val wv = WebView(context.applicationContext)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            userAgentString = USER_AGENT
            loadWithOverviewMode = true
            useWideViewPort = true
            mediaPlaybackRequiresUserGesture = false
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "onPageFinished: $url")
                if (!url.isNullOrEmpty() && url.contains("xiaohongshu.com")) {
                    if (view != null) waitForMnsv2(view)
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean = false
        }
        // 必须附着到窗口（1x1 不可见），否则部分机型不执行 JS
        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
            val params = android.view.WindowManager.LayoutParams(
                1, 1,
                android.view.WindowManager.LayoutParams.TYPE_APPLICATION,
                android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                android.graphics.PixelFormat.TRANSLUCENT,
            )
            wm.addView(wv, params)
        } catch (e: Throwable) {
            Log.w(TAG, "attach WebView failed: ${e.message}")
        }
        wv.layoutParams = ViewGroup.LayoutParams(1, 1)
        webView = wv
        return wv
    }

    /** 轮询等待页面注入 window.mnsv2。 */
    private fun waitForMnsv2(view: WebView) {
        var attempts = 0
        fun check() {
            if (pageReady) return
            if (attempts > 20) {
                Log.w(TAG, "mnsv2 not found after retries")
                readyDeferred.complete(false)
                return
            }
            attempts++
            view.evaluateJavascript("typeof window.mnsv2") { value ->
                if (value != null && value.contains("function")) {
                    pageReady = true
                    Log.d(TAG, "window.mnsv2 ready")
                    readyDeferred.complete(true)
                } else {
                    main.postDelayed({ check() }, 500)
                }
            }
        }
        main.postDelayed({ check() }, 300)
    }

    suspend fun warmUp(): Boolean = withContext(Dispatchers.Main) {
        val wv = ensureWebView()
        if (pageReady) return@withContext true
        if (!readyDeferred.isCompleted) {
            wv.loadUrl(HOME_URL)
        }
        withTimeoutOrNull(READY_TIMEOUT_MS) { readyDeferred.await() } ?: pageReady
    }

    /** 生成签名头；失败返回 null。 */
    suspend fun sign(path: String, bodyJson: String): SignHeaders? = withContext(Dispatchers.Main) {
        if (!pageReady) {
            val ok = warmUp()
            if (!ok) {
                Log.w(TAG, "sign aborted: page not ready")
                return@withContext null
            }
        }
        val wv = ensureWebView()
        val cookieA1 = readCookie("a1") ?: ""
        val script = "var __a1 = ${JSONObject.quote(cookieA1)};\n" + helpers + "\n" +
            signScript.format(
                JSONObject.quote(path),
                JSONObject.quote(bodyJson),
                "__md5",
                "__md5",
                "__b64c",
                "__a1",
                347561857,
                "__b64c",
            )
        val raw = evalAsync(wv, script)
        parseSignResult(raw, cookieA1)
    }

    private fun parseSignResult(raw: String?, cookieA1: String): SignHeaders? {
        if (raw.isNullOrBlank()) return null
        // evaluateJavascript 返回的是 JSON 编码后的字符串，需要再解一层
        val outer = runCatching { org.json.JSONTokener(raw).nextValue() as? String }.getOrNull() ?: return null
        val obj = runCatching { JSONObject(outer) }.getOrNull() ?: return null
        obj.optString("error").takeIf { it.isNotEmpty() }?.let {
            Log.w(TAG, "sign error: $it")
            return null
        }
        val xs = obj.optString("xs")
        val xt = obj.optString("xt")
        val common = obj.optString("common")
        if (xs.isEmpty() || xt.isEmpty()) return null
        return SignHeaders(xs = xs, xt = xt, xSCommon = common, a1 = cookieA1)
    }

    private suspend fun evalAsync(view: WebView, script: String): String? =
        suspendCancellableCoroutine { cont ->
            main.post {
                try {
                    view.evaluateJavascript(script) { value ->
                        if (cont.isActive) cont.resume(value)
                    }
                } catch (e: Throwable) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }

    fun readCookie(name: String): String? {
        val all = CookieManager.getInstance().getCookie("https://www.xiaohongshu.com") ?: return null
        return all.split(";").map { it.trim() }.firstOrNull { it.startsWith("$name=") }
            ?.substringAfter("=")
    }

    fun hasLoginSession(): Boolean = !readCookie("web_session").isNullOrEmpty()

    fun destroy() {
        main.post {
            webView?.let {
                (it.parent as? ViewGroup)?.removeView(it)
                it.destroy()
            }
            webView = null
            pageReady = false
            readyDeferred = CompletableDeferred()
        }
    }
}

data class SignHeaders(val xs: String, val xt: String, val xSCommon: String, val a1: String)
