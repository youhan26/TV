package com.github.catvod.spider;

import android.content.Context;

import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 爱看机器人 ikanbot.com 内置爬虫。
 *
 * <p>移植自原独立 App 的 Parser.kt + IkanbotRepository.kt，数据链路：</p>
 * <pre>
 *   列表 /hot/index-{movie|tv}-热门[-p-N].html  -> a.item
 *   详情 /play/{id} -> hidden e_token/mtype -> computeToken() -> /api/getResN -> 线路(m3u8)
 *   搜索 /search?q=... -> div.media -> “下一页”游标(n)
 * </pre>
 */
public class Ikanbot extends Spider {

    private static final String DEFAULT_HOST = "https://www1.ikanbot.com";
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final String HOT = "%E7%83%AD%E9%97%A8"; // “热门”
    private static final long CACHE_TTL = 30 * 60_000L; // 30 分钟
    private static final Pattern ID_REGEX = Pattern.compile("/play/(\\d+)");

    private static final Map<String, Cached> CACHE = new ConcurrentHashMap<>();

    private static final class Cached {

        final String body;
        final long time;

        Cached(String body, long time) {
            this.body = body;
            this.time = time;
        }
    }

    private String host = DEFAULT_HOST;
    private OkHttpClient redirectClient;
    private Context context;

    // 搜索分页游标：本站 /search 用 search_after 游标(n) 翻页，必须沿用上一页返回的“下一页”链接。
    private String searchKey = "";
    private String searchNextUrl = "";

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context);
        this.context = context;
        try {
            String h = new JSONObject(extend).optString("host");
            if (h != null && !h.isEmpty()) host = h;
        } catch (Throwable ignored) {
        }
    }

    // ---------- HTTP ----------

    private OkHttpClient redirectClient() {
        if (redirectClient == null) redirectClient = OkHttp.client().newBuilder().followRedirects(true).followSslRedirects(true).build();
        return redirectClient;
    }

    private String fetch(String url, String referer) throws IOException {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", UA);
        headers.put("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        if (referer != null && !referer.isEmpty()) headers.put("Referer", referer);
        Request request = new Request.Builder().url(url).headers(Headers.of(headers)).build();
        try (Response res = redirectClient().newCall(request).execute()) {
            if (!res.isSuccessful()) throw new IOException("HTTP " + res.code() + " for " + url);
            if (res.body() == null) throw new IOException("empty body for " + url);
            return res.body().string();
        }
    }

    private String fetchCached(String url, String referer) throws IOException {
        Cached cached = CACHE.get(url);
        if (cached != null && System.currentTimeMillis() - cached.time < CACHE_TTL) return cached.body;
        File file = cacheFile(url);
        String disk = readCache(file);
        if (disk != null) {
            CACHE.put(url, new Cached(disk, file.lastModified()));
            return disk;
        }
        String body = fetch(url, referer);
        CACHE.put(url, new Cached(body, System.currentTimeMillis()));
        writeCache(file, body);
        return body;
    }

    private File cacheFile(String url) {
        if (context == null) return null;
        return new File(new File(context.getCacheDir(), "ikanbot_cache"), Integer.toHexString(url.hashCode()) + ".cache");
    }

    private String readCache(File file) {
        if (file == null || !file.exists()) return null;
        if (System.currentTimeMillis() - file.lastModified() >= CACHE_TTL) return null;
        try (FileInputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private void writeCache(File file, String body) {
        if (file == null) return;
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        }
    }

    private String abs(String path) {
        return path.startsWith("http") ? path : host + path;
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s == null ? "" : s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    // ---------- 首页 / 分类 ----------

    @Override
    public String homeContent(boolean filter) throws Exception {
        JSONArray types = new JSONArray();
        types.put(new JSONObject().put("type_id", "movie").put("type_name", "电影"));
        types.put(new JSONObject().put("type_id", "tv").put("type_name", "剧集"));
        JSONObject result = new JSONObject();
        result.put("class", types);
        result.put("list", parseItems(fetchCached(abs("/"), null)));
        return result.toString();
    }

    @Override
    public String homeVideoContent() throws Exception {
        return "";
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        int page = parseInt(pg, 1);
        String kind = "tv".equals(tid) ? "index-tv" : "index-movie";
        String path = page <= 1 ? "/hot/" + kind + "-" + HOT + ".html" : "/hot/" + kind + "-" + HOT + "-p-" + page + ".html";
        JSONArray list = parseItems(fetchCached(abs(path), null));
        JSONObject result = new JSONObject();
        result.put("page", page);
        result.put("pagecount", list.length() == 0 ? Math.max(1, page - 1) : page + 1);
        result.put("limit", 20);
        result.put("total", 0);
        result.put("list", list);
        return result.toString();
    }

    // ---------- 详情 ----------

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String videoId = ids != null && !ids.isEmpty() ? ids.get(0) : "";
        if (videoId.isEmpty()) return "{}";

        String playUrl = abs("/play/" + videoId);
        Document doc = Jsoup.parse(fetchCached(playUrl, null));

        String curId = hidden(doc, "current_id");
        if (curId.isEmpty()) curId = videoId;
        String eToken = hidden(doc, "e_token");
        int mtype = parseInt(hidden(doc, "mtype"), 2);
        if (mtype != 1) mtype = 2;

        String title = text(doc, "h1#video_title");
        String cover = doc.selectFirst("img.cover") != null ? doc.selectFirst("img.cover").attr("data-src") : "";

        List<String> metas = new ArrayList<>();
        for (Element m : doc.select(".result-info .meta")) {
            String t = m.text().trim();
            if (!t.isEmpty()) metas.add(t);
        }
        String year = "";
        String region = "";
        int yearIdx = -1;
        for (int i = 0; i < metas.size(); i++) {
            if (metas.get(i).matches("\\d{4}")) {
                year = metas.get(i);
                yearIdx = i;
                break;
            }
        }
        if (yearIdx >= 0 && yearIdx + 1 < metas.size()) region = metas.get(yearIdx + 1);
        String last = metas.isEmpty() ? "" : metas.get(metas.size() - 1);
        String director = "";
        String actor = "";
        int slash = last.indexOf('/');
        if (slash > 0) {
            director = last.substring(0, slash).trim();
            actor = last.substring(slash + 1).trim();
        } else if (!last.isEmpty()) {
            director = last;
        }

        String token = computeToken(curId, eToken);
        String apiUrl = abs("/api/getResN?videoId=" + curId + "&mtype=" + mtype + "&token=" + token);
        List<List<Ep>> lines = parseLines(fetchCached(apiUrl, playUrl));

        StringBuilder from = new StringBuilder();
        StringBuilder urls = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            List<Ep> eps = lines.get(i);
            if (i > 0) {
                from.append("$$$");
                urls.append("$$$");
            }
            from.append("线路").append(i + 1);
            for (int j = 0; j < eps.size(); j++) {
                Ep ep = eps.get(j);
                if (j > 0) urls.append('#');
                urls.append(ep.name).append('$').append(ep.url);
            }
        }

        JSONObject vod = new JSONObject();
        vod.put("vod_id", curId);
        vod.put("vod_name", title);
        vod.put("vod_pic", cover);
        vod.put("type_name", mtype == 1 ? "电影" : "剧集");
        vod.put("vod_year", year);
        vod.put("vod_area", region);
        vod.put("vod_director", director);
        vod.put("vod_actor", actor);
        vod.put("vod_remarks", "");
        vod.put("vod_content", "");
        vod.put("vod_play_from", from.toString());
        vod.put("vod_play_url", urls.toString());

        JSONObject result = new JSONObject();
        result.put("list", new JSONArray().put(vod));
        return result.toString();
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        return new JSONObject().put("url", id).toString();
    }

    // ---------- 搜索 ----------

    @Override
    public synchronized String searchContent(String key, boolean quick) throws Exception {
        return doSearch(key, 1);
    }

    @Override
    public synchronized String searchContent(String key, boolean quick, String pg) throws Exception {
        return doSearch(key, parseInt(pg, 1));
    }

    private String doSearch(String key, int page) throws Exception {
        String path;
        if (page <= 1 || !key.equals(searchKey) || searchNextUrl.isEmpty()) {
            path = "/search?q=" + URLEncoder.encode(key, "UTF-8");
        } else {
            path = searchNextUrl;
        }
        Document doc = Jsoup.parse(fetch(abs(path), null));
        searchKey = key;
        searchNextUrl = extractNext(doc);
        JSONArray list = parseSearch(doc);
        JSONObject result = new JSONObject();
        result.put("page", page);
        result.put("pagecount", searchNextUrl.isEmpty() ? page : page + 1);
        result.put("limit", 20);
        result.put("total", 0);
        result.put("list", list);
        return result.toString();
    }

    private String extractNext(Document doc) {
        for (Element a : doc.select("a[href]")) {
            if (a.ownText().contains("下一页")) return a.attr("href");
        }
        return "";
    }

    // ---------- 解析 ----------

    private JSONArray parseItems(String html) {
        JSONArray arr = new JSONArray();
        try {
            for (Element a : Jsoup.parse(html).select("a.item")) {
                Matcher m = ID_REGEX.matcher(a.attr("href"));
                if (!m.find()) continue;
                Element img = a.selectFirst("img");
                if (img == null) continue;
                String title = img.attr("alt").trim();
                if (title.isEmpty()) {
                    Element p = a.selectFirst("p");
                    title = p == null ? "" : p.text().trim();
                }
                if (title.isEmpty()) continue;
                arr.put(new JSONObject()
                        .put("vod_id", m.group(1))
                        .put("vod_name", title)
                        .put("vod_pic", img.attr("data-src"))
                        .put("vod_remarks", ""));
            }
        } catch (Throwable ignored) {
        }
        return arr;
    }

    private JSONArray parseSearch(Document doc) {
        JSONArray arr = new JSONArray();
        try {
            for (Element a : doc.select("a.cover-link")) {
                Matcher m = ID_REGEX.matcher(a.attr("href"));
                if (!m.find()) continue;
                Element img = a.selectFirst("img.media-pic");
                if (img == null) continue;
                String title = img.attr("alt").trim();
                if (title.isEmpty()) continue;
                arr.put(new JSONObject()
                        .put("vod_id", m.group(1))
                        .put("vod_name", title)
                        .put("vod_pic", img.attr("data-src"))
                        .put("vod_remarks", ""));
            }
        } catch (Throwable ignored) {
        }
        return arr;
    }

    private String hidden(Document doc, String id) {
        Element e = doc.selectFirst("input#" + id);
        return e == null ? "" : e.attr("value");
    }

    private String text(Document doc, String selector) {
        Element e = doc.selectFirst(selector);
        return e == null ? "" : e.text().trim();
    }

    /**
     * 还原站点 get_tks() 算法：取 videoId 末 4 位数字，每位 d%3+1 决定从 e_token 中切出 8 个字符拼接。
     */
    private String computeToken(String videoId, String eToken) {
        String last4 = videoId.length() >= 4 ? videoId.substring(videoId.length() - 4) : videoId;
        String token = eToken;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < last4.length(); i++) {
            char c = last4.charAt(i);
            if (c < '0' || c > '9') break;
            int step = (c - '0') % 3 + 1;
            if (step + 8 > token.length()) break;
            sb.append(token, step, step + 8);
            token = token.substring(step + 8);
        }
        return sb.toString();
    }

    private List<List<Ep>> parseLines(String json) {
        List<List<Ep>> lines = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(json);
            if (root.optInt("state", -1) != 1) return lines;
            JSONObject data = root.optJSONObject("data");
            JSONArray list = data == null ? null : data.optJSONArray("list");
            if (list == null) return lines;
            for (int i = 0; i < list.length(); i++) {
                JSONObject obj = list.optJSONObject(i);
                if (obj == null) continue;
                List<Ep> eps = parseResData(obj.optString("resData"));
                if (!eps.isEmpty()) lines.add(eps);
            }
        } catch (Throwable ignored) {
        }
        return lines;
    }

    /**
     * resData 形如 [{"flag":"gsm3u8","url":"第01集$https://.../index.m3u8#第02集$https://..."}]
     * url 字段以 # 分隔多个资源，每个资源以第一个 $ 分隔名称与地址。
     */
    private List<Ep> parseResData(String resData) {
        List<Ep> result = new ArrayList<>();
        if (resData == null || resData.trim().isEmpty()) return result;
        try {
            JSONArray arr = new JSONArray(resData);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.optJSONObject(i);
                if (item == null) continue;
                String urlField = item.optString("url");
                String newName = item.optString("newName");
                for (String seg : urlField.split("#")) {
                    if (seg.trim().isEmpty()) continue;
                    int idx = seg.indexOf('$');
                    if (idx <= 0) continue;
                    String name = seg.substring(0, idx).trim();
                    String url = seg.substring(idx + 1).trim();
                    if (!url.toLowerCase().endsWith(".m3u8")) continue;
                    if (newName != null && !newName.trim().isEmpty() && !newName.matches("\\d+")) name = newName;
                    result.add(new Ep(name, url));
                }
            }
        } catch (Throwable ignored) {
        }
        return result;
    }

    private static class Ep {
        final String name;
        final String url;

        Ep(String name, String url) {
            this.name = name;
            this.url = url;
        }
    }
}
