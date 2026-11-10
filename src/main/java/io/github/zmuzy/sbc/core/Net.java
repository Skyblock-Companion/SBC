// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.internal.LazilyParsedNumber;
import com.google.gson.stream.JsonReader;
import io.github.zmuzy.sbc.SbcMod;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

// small async json fetcher (hypixel, mojang), read only
public final class Net {
    private static final HttpClient CLIENT = HttpClient.newBuilder().sslContext(io.github.zmuzy.sbc.core.Tls.context())
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private Net() {}

    public static CompletableFuture<JsonElement> getJson(String url, String authValue) {
        return getJson(url, "API-Key", authValue);
    }

    // GET with an optional auth header
    public static CompletableFuture<JsonElement> getJson(String url, String header, String value) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "SkyBlockCompanion/1.0 (Fabric mod)")
                .GET();
        if (value != null && !value.isBlank()) b.header(header, value.trim());
        return CLIENT.sendAsync(b.build(), HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(r -> {
                    if (r.statusCode() / 100 != 2) throw new IllegalStateException("HTTP " + r.statusCode() + " for " + strip(url));
                    return parse(r.body());
                })
                .whenComplete((v, e) -> {
                    if (e != null) SbcMod.LOG.warn("Request failed: {}", e.getMessage());
                });
    }

    // raw bytes (map images), max 16 MB
    public static CompletableFuture<byte[]> getBytes(String url) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
                .header("User-Agent", "SkyBlockCompanion/1.0 (Fabric mod)").GET().build();
        return CLIENT.sendAsync(req, HttpResponse.BodyHandlers.ofByteArray()).thenApply(r -> {
            if (r.statusCode() / 100 != 2) throw new IllegalStateException("HTTP " + r.statusCode());
            if (r.body().length > 16 * 1024 * 1024) throw new IllegalStateException("image too large");
            return r.body();
        });
    }

    // nothing reads these, auction lore is most of a page
    private static final java.util.Set<String> SKIP = java.util.Set.of("item_lore");

    // straight from the bytes, no big string copy
    static JsonElement parse(byte[] body) {
        if (body.length == 0) return JsonNull.INSTANCE;
        try (JsonReader in = new JsonReader(new InputStreamReader(new ByteArrayInputStream(body), StandardCharsets.UTF_8))) {
            in.setLenient(true);
            return tree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static JsonElement tree(JsonReader in) throws IOException {
        switch (in.peek()) {
            case BEGIN_OBJECT -> {
                JsonObject o = new JsonObject();
                in.beginObject();
                while (in.hasNext()) {
                    String k = in.nextName();
                    if (SKIP.contains(k)) in.skipValue();
                    else o.add(k, tree(in));
                }
                in.endObject();
                return o;
            }
            case BEGIN_ARRAY -> {
                JsonArray a = new JsonArray();
                in.beginArray();
                while (in.hasNext()) a.add(tree(in));
                in.endArray();
                return a;
            }
            case STRING -> {
                return new JsonPrimitive(in.nextString());
            }
            case NUMBER -> {
                return new JsonPrimitive(new LazilyParsedNumber(in.nextString()));
            }
            case BOOLEAN -> {
                return new JsonPrimitive(in.nextBoolean());
            }
            case NULL -> {
                in.nextNull();
                return JsonNull.INSTANCE;
            }
            default -> throw new IOException("unexpected " + in.peek());
        }
    }

    private static String strip(String url) {
        int q = url.indexOf('?');
        return q < 0 ? url : url.substring(0, q);
    }
}
