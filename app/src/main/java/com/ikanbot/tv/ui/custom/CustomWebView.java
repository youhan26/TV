package com.ikanbot.tv.ui.custom;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.net.http.SslError;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;

import com.ikanbot.tv.App;
import com.ikanbot.tv.Constant;
import com.ikanbot.tv.api.config.RuleConfig;
import com.ikanbot.tv.api.config.VodConfig;
import com.ikanbot.tv.impl.ParseCallback;
import com.ikanbot.tv.setting.Setting;
import com.ikanbot.tv.ui.dialog.WebDialog;
import com.ikanbot.tv.utils.Sniffer;
import com.ikanbot.tv.utils.Task;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;
import com.google.common.net.HttpHeaders;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.Response;

public class CustomWebView extends WebView implements DialogInterface.OnDismissListener {

    private static final String TAG = CustomWebView.class.getSimpleName();

    private static final Pattern PLAYER = Pattern.compile("player.*https?://");
    private static final Pattern EXTINF = Pattern.compile("#EXTINF:\\s*([0-9.]+)");
    private static final List<String> SECOND_LEVEL = Arrays.asList("com.cn", "net.cn", "org.cn", "gov.cn", "edu.cn", "co.jp", "ne.jp", "or.jp", "co.kr", "com.tw", "com.hk", "com.au", "co.uk", "com.sg");
    private static final String BLANK = "about:blank";
    private static final int MAX_URLS = 5;
    private static final long MIN_MEDIA_MS = 10_000L;

    private final AtomicReference<ParseCallback> callbackRef = new AtomicReference<>();
    private final Set<String> rejected = Collections.synchronizedSet(new HashSet<>());
    private LinkedHashSet<String> urls;
    private WebResourceResponse empty;
    private volatile String mediaDomain;
    private WebDialog dialog;
    private Runnable timer;
    private boolean stopped;
    private boolean detect;
    private String click;
    private String from;
    private String key;
    private String url;

    public static CustomWebView create(@NonNull Context context) {
        return new CustomWebView(context);
    }

    private CustomWebView(@NonNull Context context) {
        super(context);
        initSettings();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initSettings() {
        timer = () -> stop(true);
        urls = new LinkedHashSet<>();
        empty = new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream("".getBytes()));
        WebSettings setting = getSettings();
        setting.setSupportZoom(true);
        setting.setUseWideViewPort(true);
        setting.setDatabaseEnabled(true);
        setting.setDomStorageEnabled(true);
        setting.setJavaScriptEnabled(true);
        setting.setBuiltInZoomControls(true);
        setting.setDisplayZoomControls(false);
        setting.setLoadWithOverviewMode(true);
        setting.setUserAgentString(Setting.getUa());
        setting.setMediaPlaybackRequiresUserGesture(false);
        setting.setJavaScriptCanOpenWindowsAutomatically(false);
        setting.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        setWebViewClient(webViewClient());
    }

    public CustomWebView start(String key, String from, Map<String, String> headers, String url, String click, ParseCallback callback, boolean detect) {
        SpiderDebug.log(TAG, "key=%s, from=%s, click=%s, url=%s, headers=%s", key, from, click, url, headers);
        App.post(timer, Constant.TIMEOUT_PARSE_WEB);
        callbackRef.set(callback);
        this.detect = detect;
        this.click = click;
        this.from = from;
        this.key = key;
        this.url = url;
        start(headers);
        return this;
    }

    private void start(Map<String, String> headers) {
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true);
        checkHeader(url, headers);
        loadUrl(url, headers);
    }

    private void checkHeader(String url, Map<String, String> headers) {
        for (String key : headers.keySet()) {
            if (HttpHeaders.USER_AGENT.equalsIgnoreCase(key)) getSettings().setUserAgentString(headers.get(key));
            else if (HttpHeaders.COOKIE.equalsIgnoreCase(key)) CookieManager.getInstance().setCookie(url, headers.get(key));
        }
    }

    private WebViewClient webViewClient() {
        return new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                String host = request.getUrl().getHost();
                if (TextUtils.isEmpty(host) || isAd(host)) return empty;
                Map<String, String> headers = request.getRequestHeaders();
                if (url.contains("/cdn-cgi/challenge-platform/")) post(() -> showDialog());
                if (detect && PLAYER.matcher(url).find() && addUrl(url)) onParseAdd(headers, url);
                else if (isVideoFormat(url)) checkMedia(headers, url);
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (url.equals(BLANK)) return;
                evaluate(getScript(url), 0);
            }

            @Override
            @SuppressLint("WebViewClientOnReceivedSslError")
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }
        };
    }

    private boolean addUrl(String url) {
        if (urls.size() > MAX_URLS) urls.clear();
        return urls.add(url);
    }

    private void showDialog() {
        if (dialog != null || App.activity() == null) return;
        if (getParent() != null) ((ViewGroup) getParent()).removeView(this);
        dialog = WebDialog.create(this).show();
        App.removeCallbacks(timer);
    }

    private void hideDialog() {
        if (dialog != null) dialog.dismiss();
        dialog = null;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        stop(true);
    }

    private List<String> getScript(String url) {
        List<String> script = new ArrayList<>(Sniffer.getScript(Uri.parse(url)));
        if (TextUtils.isEmpty(click) || script.contains(click)) return script;
        script.add(0, click);
        return script;
    }

    private void evaluate(List<String> script, int index) {
        if (index >= script.size()) return;
        String js = script.get(index);
        if (TextUtils.isEmpty(js)) {
            evaluate(script, index + 1);
        } else {
            evaluateJavascript(js, value -> evaluate(script, index + 1));
        }
    }

    private boolean isAd(String host) {
        for (String ad : RuleConfig.get().getAds()) if (Util.containOrMatch(host, ad)) return true;
        return false;
    }

    private boolean isVideoFormat(String url) {
        try {
            if (!detect && url.equals(this.url)) return false;
            Spider spider = VodConfig.get().getSite(key).spider();
            if (spider.manualVideoCheck()) return spider.isVideoFormat(url);
            return Sniffer.isVideoFormat(url);
        } catch (Exception ignored) {
            return Sniffer.isVideoFormat(url);
        }
    }

    private void onParseAdd(Map<String, String> headers, String url) {
        ParseCallback cb = callbackRef.get();
        if (cb == null) return;
        post(() -> CustomWebView.create(App.get()).start(key, from, headers, url, click, cb, false));
    }

    /**
     * 嗅探到的媒体先过两道校验，避免把广告当成正片交出去：
     * 1. 域名一致：记录已接受媒体的域名，之后域名不一致的丢弃（广告多来自跳转后的广告站）；
     * 2. 时长下限：小于 {@link #MIN_MEDIA_MS} 的丢弃（前置广告通常只有几秒）。
     */
    private void checkMedia(Map<String, String> headers, String url) {
        if (stopped || rejected.contains(url)) return;
        String domain = domain(url);
        String accepted = mediaDomain;
        if (accepted != null && !accepted.equals(domain)) {
            rejected.add(url);
            SpiderDebug.log(TAG, "drop media by domain: %s (accepted=%s)", url, accepted);
            return;
        }
        Task.execute(() -> {
            long duration = probeDuration(url, headers, 0);
            App.post(() -> {
                if (stopped || rejected.contains(url)) return;
                if (duration >= 0 && duration < MIN_MEDIA_MS) {
                    rejected.add(url);
                    SpiderDebug.log(TAG, "drop media by duration: %dms %s", duration, url);
                    return;
                }
                if (mediaDomain == null) mediaDomain = domain;
                SpiderDebug.log(TAG, "accept media: duration=%dms %s", duration, url);
                onParseSuccess(headers, url);
            });
        });
    }

    private long probeDuration(String url, Map<String, String> headers, int depth) {
        try {
            return isHls(url) ? hlsDuration(url, headers, depth) : mediaDuration(url, headers);
        } catch (Throwable e) {
            return -1;
        }
    }

    private boolean isHls(String url) {
        String path = Uri.parse(url).getPath();
        return path != null && path.toLowerCase().contains(".m3u8");
    }

    private long hlsDuration(String url, Map<String, String> headers, int depth) throws Exception {
        if (depth > 2) return -1;
        try (Response res = request(url, headers).execute()) {
            String text = res.body().string();
            if (!text.contains("#EXTM3U")) return -1;
            if (text.contains("#EXT-X-STREAM-INF")) {
                for (String line : text.split("\n")) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    HttpUrl base = HttpUrl.parse(url);
                    return hlsDuration(base == null ? line : base.resolve(line).toString(), headers, depth + 1);
                }
                return -1;
            }
            if (!text.contains("#EXT-X-ENDLIST")) return -1;
            Matcher matcher = EXTINF.matcher(text);
            double total = 0;
            while (matcher.find()) total += Double.parseDouble(matcher.group(1));
            return (long) (total * 1000);
        }
    }

    private long mediaDuration(String url, Map<String, String> headers) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            if (headers == null || headers.isEmpty()) retriever.setDataSource(url);
            else retriever.setDataSource(url, headers);
            String value = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return TextUtils.isEmpty(value) ? -1 : Long.parseLong(value);
        } catch (Throwable e) {
            return -1;
        } finally {
            try {
                retriever.release();
            } catch (Throwable ignored) {
            }
        }
    }

    private Call request(String url, Map<String, String> headers) {
        return headers == null || headers.isEmpty() ? OkHttp.newCall(url) : OkHttp.newCall(url, headers);
    }

    private String domain(String url) {
        String host = Uri.parse(url).getHost();
        if (TextUtils.isEmpty(host)) return "";
        if (host.matches("\\d+(\\.\\d+){3}")) return host;
        String[] parts = host.split("\\.");
        if (parts.length <= 2) return host;
        String last = parts[parts.length - 2] + "." + parts[parts.length - 1];
        return SECOND_LEVEL.contains(last) && parts.length >= 3 ? parts[parts.length - 3] + "." + last : last;
    }

    private void onParseSuccess(Map<String, String> headers, String url) {
        ParseCallback cb = callbackRef.getAndSet(null);
        if (cb != null) cb.onParseSuccess(headers, url, from);
        post(() -> stop(false));
    }

    private void onParseError() {
        ParseCallback cb = callbackRef.getAndSet(null);
        if (cb != null) cb.onParseError();
    }

    public void stop(boolean error) {
        if (stopped) return;
        stopped = true;
        hideDialog();
        stopLoading();
        loadUrl(BLANK);
        App.removeCallbacks(timer);
        if (error) onParseError();
        else callbackRef.set(null);
    }
}
