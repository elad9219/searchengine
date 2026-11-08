package com.handson.searchengine.crawler;

import okhttp3.Dns;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.net.SocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PublicPageFetcherTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1/", "http://127.1/", "http://2130706433/",
            "http://10.0.0.1/", "http://100.64.0.1/", "http://169.254.169.254/",
            "http://[::1]/", "http://[::ffff:127.0.0.1]/", "http://[fc00::1]/",
            "http://[64:ff9b::7f00:1]/", "http://[2002:7f00:1::1]/",
            "https://example.com:8443/", "https://user:password@example.com/"
    })
    void rejectsUnsafeTargetsBeforeSendingARequest(String target) {
        PublicPageFetcher fetcher = new PublicPageFetcher(Dns.SYSTEM,
                new OkHttpClient.Builder().addInterceptor(chain -> fail("Request should be blocked")));

        assertThrows(PublicPageFetcher.RejectedTargetException.class,
                () -> fetcher.fetch(target, 1000));
    }

    @Test
    void rejectsADomainWithMixedPublicAndPrivateAddresses() {
        PublicPageFetcher fetcher = new PublicPageFetcher(
                host -> List.of(InetAddress.getByName("1.1.1.1"), InetAddress.getByName("192.168.1.2")),
                new OkHttpClient.Builder().addInterceptor(chain -> fail("Request should be blocked")));

        assertThrows(PublicPageFetcher.RejectedTargetException.class,
                () -> fetcher.fetch("https://example.com/", 1000));
    }

    @Test
    void checksDnsAgainBeforeFollowingARedirectToTheSameHost() {
        AtomicInteger lookups = new AtomicInteger();
        AtomicInteger requests = new AtomicInteger();
        PublicPageFetcher fetcher = new PublicPageFetcher(
                host -> List.of(InetAddress.getByName(lookups.incrementAndGet() == 1
                        ? "1.1.1.1" : "127.0.0.1")),
                new OkHttpClient.Builder().addInterceptor(chain -> {
                    requests.incrementAndGet();
                    return redirect(chain.request(), "/next");
                }));

        assertThrows(PublicPageFetcher.RejectedTargetException.class,
                () -> fetcher.fetch("https://example.com/", 1000));
        assertEquals(2, lookups.get());
        assertEquals(1, requests.get());
    }

    @Test
    void blocksARedirectToLoopbackBeforeSendingASecondRequest() {
        AtomicInteger requests = new AtomicInteger();
        PublicPageFetcher fetcher = new PublicPageFetcher(
                host -> List.of(InetAddress.getByName(host.equals("example.com") ? "1.1.1.1" : host)),
                new OkHttpClient.Builder().addInterceptor(chain -> {
                    requests.incrementAndGet();
                    return redirect(chain.request(), "http://127.0.0.1/");
                }));

        assertThrows(PublicPageFetcher.RejectedTargetException.class,
                () -> fetcher.fetch("https://example.com/", 1000));
        assertEquals(1, requests.get());
    }

    @Test
    void followsAPublicRedirectAndUsesItsUrlToResolveLinks() throws IOException {
        PublicPageFetcher fetcher = new PublicPageFetcher(
                host -> List.of(InetAddress.getByName("2606:4700:4700::1111")),
                new OkHttpClient.Builder().addInterceptor(chain -> {
                    Request request = chain.request();
                    if (request.url().encodedPath().equals("/")) {
                        return redirect(request, "https://www.example.com/docs/index.html");
                    }
                    return response(request, 200, "OK")
                            .body(ResponseBody.create("<a href='next.html'>Next</a>",
                                    MediaType.parse("text/html; charset=UTF-8")))
                            .build();
                }));

        Document page = fetcher.fetch("https://example.com/", 1000);
        assertEquals("https://www.example.com/docs/next.html",
                page.selectFirst("a").absUrl("href"));
    }

    @Test
    void connectsUsingTheAddressThatPassedValidation() throws IOException {
        InetAddress publicAddress = InetAddress.getByName("1.1.1.1");
        Dns resolver = mock(Dns.class);
        when(resolver.lookup("example.com")).thenReturn(List.of(publicAddress));
        SocketFactory socketFactory = mock(SocketFactory.class);
        Socket socket = mock(Socket.class);
        when(socketFactory.createSocket()).thenReturn(socket);
        InetSocketAddress destination = new InetSocketAddress(publicAddress, 80);
        doThrow(new IOException("No network connection made")).when(socket).connect(eq(destination), anyInt());

        PublicPageFetcher fetcher = new PublicPageFetcher(resolver,
                new OkHttpClient.Builder().socketFactory(socketFactory));

        assertThrows(IOException.class, () -> fetcher.fetch("http://example.com/", 1000));
        verify(socket).connect(eq(destination), anyInt());
        verify(resolver, times(1)).lookup("example.com");
    }

    private static Response redirect(Request request, String target) {
        return response(request, 302, "Found")
                .header("Location", target)
                .body(ResponseBody.create("", null))
                .build();
    }

    private static Response.Builder response(Request request, int code, String message) {
        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(message);
    }
}