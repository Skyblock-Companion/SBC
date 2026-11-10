// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.core.data;

import io.muzy.sbc.core.Tls;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

// the real connection behind DataUpdater + BingoData: plain GETs with the mod name and nothing else
final class DataWeb implements DataUpdater.Web {
    static final DataWeb INSTANCE = new DataWeb();
    static final Map<String, String> PLAIN_HEADERS = Map.of();
    static final Map<String, String> API_HEADERS = Map.of("Accept", "application/vnd.github+json", "X-GitHub-Api-Version", "2022-11-28");
    private static final long MAX_BODY = 32L * 1024 * 1024;

    private HttpClient client;

    private DataWeb() {}

    static String userAgent() {
        String version = "dev";
        try {
            version = FabricLoader.getInstance().getModContainer("sbcompanion").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse(version);
        } catch (Throwable ignored) {
        }
        return "SkyBlockCompanion/" + version;
    }

    private synchronized HttpClient client() {
        if (client == null) {
            client = HttpClient.newBuilder().sslContext(Tls.context()).connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL).build();
        }
        return client;
    }

    @Override
    public DataUpdater.Reply get(String url, Map<String, String> headers) throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).header("User-Agent", userAgent()).GET();
        headers.forEach(b::header);
        HttpResponse<byte[]> r = client().send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
        if (r.body().length > MAX_BODY) throw new IOException("answer too large from " + url);
        return new DataUpdater.Reply(r.statusCode(), r.body(), r.headers().firstValue("etag").orElse(""));
    }
}
