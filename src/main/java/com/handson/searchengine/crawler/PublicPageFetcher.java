package com.handson.searchengine.crawler;

import okhttp3.Call;
import okhttp3.ConnectionPool;
import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class PublicPageFetcher {
    private static final int MAX_REDIRECTS = 5;
    private static final int MAX_BODY_BYTES = 2 * 1024 * 1024;

    private final Dns resolver;
    private final OkHttpClient client;

    PublicPageFetcher() {
        this(Dns.SYSTEM, new OkHttpClient.Builder());
    }

    PublicPageFetcher(Dns resolver, OkHttpClient.Builder builder) {
        this.resolver = resolver;
        this.client = builder
                .proxy(Proxy.NO_PROXY)
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(0, 1, TimeUnit.SECONDS))
                .build();
    }

    Document fetch(String url, long timeoutMillis) throws IOException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        HttpUrl target = HttpUrl.parse(url);

        for (int redirects = 0; ; redirects++) {
            if (deadline - System.nanoTime() <= 0) throw new IOException("Page fetch deadline reached");
            validateTarget(target);
            String host = target.host();
            List<InetAddress> addresses = List.copyOf(resolver.lookup(host));
            if (addresses.isEmpty()) throw new UnknownHostException(host);
            if (addresses.stream().anyMatch(address -> !isPublic(address))) {
                throw new RejectedTargetException();
            }

            // Connect only to the addresses checked above while keeping the hostname for TLS.
            OkHttpClient pinnedClient = client.newBuilder().dns(requestedHost -> {
                if (!host.equals(requestedHost)) throw new UnknownHostException(requestedHost);
                return addresses;
            }).build();
            Request request = new Request.Builder()
                    .url(target)
                    .header("User-Agent", "PortfolioSearchEngine/1.0")
                    .build();
            Call call = pinnedClient.newCall(request);
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) throw new IOException("Page fetch deadline reached");
            call.timeout().timeout(remaining, TimeUnit.NANOSECONDS);

            try (Response response = call.execute()) {
                int code = response.code();
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    if (redirects >= MAX_REDIRECTS) throw new IOException("Too many redirects");
                    String location = response.header("Location");
                    if (location == null || location.isBlank()) {
                        throw new IOException("Redirect has no destination");
                    }
                    target = target.resolve(location);
                    continue;
                }
                if (!response.isSuccessful() || response.body() == null) {
                    throw new IOException("Page request failed with HTTP status " + code);
                }
                return parseBody(response.body(), target);
            }
        }
    }

    private static void validateTarget(HttpUrl target) throws RejectedTargetException {
        if (target == null || !target.username().isEmpty() || !target.password().isEmpty()
                || target.port() != (target.isHttps() ? 443 : 80)) {
            throw new RejectedTargetException();
        }
    }

    private static Document parseBody(ResponseBody body, HttpUrl target) throws IOException {
        MediaType type = body.contentType();
        if (type != null && !type.type().equals("text")
                && !(type.type().equals("application")
                && (type.subtype().equals("xml") || type.subtype().endsWith("+xml")))) {
            throw new IOException("Unsupported page content type");
        }
        byte[] bytes = body.byteStream().readNBytes(MAX_BODY_BYTES + 1);
        if (bytes.length > MAX_BODY_BYTES) throw new IOException("Page exceeds the size limit");
        Charset charset = type == null ? null : type.charset();
        return Jsoup.parse(new ByteArrayInputStream(bytes),
                charset == null ? null : charset.name(), target.toString());
    }

    private static boolean isPublic(InetAddress address) {
        byte[] bytes = address.getAddress();
        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);
        int third = Byte.toUnsignedInt(bytes[2]);

        if (bytes.length == 4) {
            // Exclude private, shared, link-local, loopback, and other special-use IPv4 ranges.
            return first != 0 && first != 10 && first != 127 && first < 224
                    && !(first == 100 && second >= 64 && second <= 127)
                    && !(first == 169 && second == 254)
                    && !(first == 172 && second >= 16 && second <= 31)
                    && !(first == 192 && (second == 168
                    || (second == 0 && (third == 0 || third == 2))
                    || (second == 88 && third == 99)))
                    && !(first == 198 && (second == 18 || second == 19
                    || (second == 51 && third == 100)))
                    && !(first == 203 && second == 0 && third == 113);
        }

        // Allow global unicast IPv6, excluding special assignments, tunnels, and documentation.
        return (first & 0xe0) == 0x20
                && !(first == 0x20 && second == 0x01
                && (third <= 1 || (third == 0x0d && Byte.toUnsignedInt(bytes[3]) == 0xb8)))
                && !(first == 0x20 && second == 0x02)
                && !(first == 0x3f && second == 0xff && (third & 0xf0) == 0);
    }

    static final class RejectedTargetException extends IOException {
        RejectedTargetException() {
            super("Only public HTTP and HTTPS targets on standard ports are allowed");
        }
    }
}