// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

// one page of the skyblock wiki, the only site the in game browser shows. comes from the wikis own api, laid
// out by WikiLayout
public final class WebPage {
    public static final String WIKI = "https://hypixelskyblock.minecraft.wiki";
    private static final HttpClient CLIENT = HttpClient.newBuilder().sslContext(dev.zmuzy.sbc.core.Tls.context()).connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    public String url = "", title = "", error = "", fragment = "";
    public Html.Node root;

    private WebPage() {}

    // on the sb wiki: the address has to really be there, not just name it
    public static boolean isWiki(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme(), host = uri.getHost();
            if (host == null || !("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))) return false;
            String h = host.toLowerCase(Locale.ROOT);
            return h.equals("hypixelskyblock.minecraft.wiki") || h.endsWith(".hypixelskyblock.minecraft.wiki");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static String wikiSearchUrl(String text) {
        return WIKI + "/index.php?search=" + URLEncoder.encode(text, StandardCharsets.UTF_8);
    }

    // the first few sentences of a wiki page in plain text (TextExtracts), "" when there is no such page
    public static CompletableFuture<String> intro(String title) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject json = api("action=query&prop=extracts&exintro=1&explaintext=1&exsentences=3&redirects=1&titles=" + URLEncoder.encode(title, StandardCharsets.UTF_8));
                JsonArray pages = json.getAsJsonObject("query").getAsJsonArray("pages");
                if (pages.isEmpty()) return "";
                JsonObject page = pages.get(0).getAsJsonObject();
                return page.has("extract") ? page.get("extract").getAsString().replaceAll("\\s+", " ").trim() : "";
            } catch (Exception e) {
                return "";
            }
        });
    }

    public static CompletableFuture<WebPage> load(String url) {
        return CompletableFuture.supplyAsync(() -> {
            WebPage p = new WebPage();
            p.url = url;
            try {
                URI uri = URI.create(url);
                if (uri.getRawFragment() != null) p.fragment = URLDecoder.decode(uri.getRawFragment(), StandardCharsets.UTF_8);
                String search = query(uri, "search");
                if (search != null) p.search(search);
                else p.article(pageOf(uri));
            } catch (Throwable e) { // whatever goes wrong must end the loading or the tab says Loading forever
                p.error = "Could not load this page: " + e.getMessage();
                p.title = "Could not load this page";
            }
            return p;
        });
    }

    // page name from an address like /w/Hyperion or /index.php?title=Hyperion
    static String pageOf(URI uri) {
        String title = query(uri, "title");
        if (title != null) return title;
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        String page = path.startsWith("/w/") ? path.substring(3) : path.replaceFirst("^/", "");
        page = URLDecoder.decode(page, StandardCharsets.UTF_8);
        return page.isEmpty() || page.equals("index.php") ? "Main Page" : page;
    }

    private static String query(URI uri, String key) {
        String raw = uri.getRawQuery();
        if (raw == null) return null;
        for (String part : raw.split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0 && part.substring(0, eq).equals(key)) return URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
        }
        return null;
    }

    private static JsonObject api(String query) throws Exception {
        // the wikis gate gives anything calling itself Mozilla a page that needs a real browser, a mod says what it is
        HttpRequest r = HttpRequest.newBuilder(URI.create(WIKI + "/api.php?format=json&formatversion=2&" + query)).timeout(Duration.ofSeconds(20))
                .header("User-Agent", "SkyBlockCompanion/1.0 (Minecraft mod; in-game wiki reader)").GET().build();
        HttpResponse<String> res = CLIENT.send(r, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) throw new IllegalStateException("HTTP " + res.statusCode());
        return JsonParser.parseString(res.body()).getAsJsonObject();
    }

    private void article(String page) throws Exception {
        JsonObject json = api("action=parse&redirects=1&disableeditsection=1&disablelimitreport=1&prop=text%7Cdisplaytitle&page=" + URLEncoder.encode(page, StandardCharsets.UTF_8));
        if (!json.has("parse")) {
            search(page.replace('_', ' '));
            return;
        }
        fromParse(json.getAsJsonObject("parse"));
    }

    void fromParse(JsonObject parse) {
        title = Html.parse(parse.get("displaytitle").getAsString()).text();
        root = Html.parse(parse.get("text").getAsString());
    }

    // a title thats exactly what was typed opens right away whatever the caps, anything else lists what the wiki finds
    private void search(String text) throws Exception {
        HttpRequest r = HttpRequest.newBuilder(URI.create(WIKI + "/api.php?format=json&action=opensearch&limit=1&redirects=resolve&search=" + URLEncoder.encode(text, StandardCharsets.UTF_8)))
                .timeout(Duration.ofSeconds(20)).header("User-Agent", "SkyBlockCompanion/1.0 (Minecraft mod; in-game wiki reader)").GET().build();
        JsonArray found = JsonParser.parseString(CLIENT.send(r, HttpResponse.BodyHandlers.ofString()).body()).getAsJsonArray();
        JsonArray names = found.get(1).getAsJsonArray();
        if (!names.isEmpty() && names.get(0).getAsString().equalsIgnoreCase(text.trim())) {
            String page = names.get(0).getAsString();
            url = WIKI + "/w/" + page.replace(' ', '_');
            article(page);
            return;
        }
        title = "Search results for \"" + text + "\"";
        JsonObject json = api("action=query&list=search&srlimit=40&srprop=snippet&srsearch=" + URLEncoder.encode(text, StandardCharsets.UTF_8));
        StringBuilder html = new StringBuilder();
        JsonArray hits = json.getAsJsonObject("query").getAsJsonArray("search");
        if (hits.isEmpty()) html.append("<p>The wiki has nothing on this.</p>");
        for (JsonElement e : hits) {
            JsonObject hit = e.getAsJsonObject();
            String name = hit.get("title").getAsString();
            html.append("<p><a href=\"/w/").append(name.replace(' ', '_').replace("\"", "%22")).append("\"><b>").append(name.replace("<", "&lt;")).append("</b></a><br>")
                    .append(hit.get("snippet").getAsString().replace("class=\"searchmatch\"", "class=\"bold\"")).append("</p>");
        }
        root = Html.parse(html.toString());
    }
}
