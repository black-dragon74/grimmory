package org.booklore.service.kobo;

import org.booklore.util.kobo.BookloreSyncTokenGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KoboReadingServicesProxyTest {

    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private BookloreSyncTokenGenerator syncTokenGenerator;
    @Mock
    private HttpClient httpClient;
    @Mock
    private HttpResponse<byte[]> httpResponse;

    @InjectMocks
    private KoboServerProxy proxy;

    @BeforeEach
    void setUp() throws Exception {
        Field httpClientField = KoboServerProxy.class.getDeclaredField("httpClient");
        httpClientField.setAccessible(true);
        httpClientField.set(proxy, httpClient);
    }

    @Test
    void preservesPathQueryBodyAndRelevantHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/v3/content/42/annotations");
        request.setQueryString("foo=bar");
        request.addHeader("Content-Type", "application/json");
        request.addHeader("X-Kobo-DeviceId", "device-id");
        byte[] requestBody = "request".getBytes();
        byte[] responseBody = "response".getBytes();
        when(httpResponse.statusCode()).thenReturn(202);
        when(httpResponse.body()).thenReturn(responseBody);
        when(httpResponse.headers()).thenReturn(HttpHeaders.of(
                Map.of("content-type", List.of("application/json"), "x-kobo-test", List.of("value")),
                (name, value) -> true));
        when(httpClient.<byte[]>send(any(HttpRequest.class), any())).thenReturn(httpResponse);

        ResponseEntity<byte[]> response = proxy.proxyToReadingServices(request, requestBody);

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).<byte[]>send(requestCaptor.capture(), any());
        HttpRequest upstreamRequest = requestCaptor.getValue();
        assertEquals("https://readingservices.kobo.com/api/v3/content/42/annotations?foo=bar",
                upstreamRequest.uri().toString());
        assertEquals("PATCH", upstreamRequest.method());
        assertEquals(requestBody.length, upstreamRequest.bodyPublisher().orElseThrow().contentLength());
        assertEquals("application/json", upstreamRequest.headers().firstValue("Content-Type").orElseThrow());
        assertEquals("device-id", upstreamRequest.headers().firstValue("X-Kobo-DeviceId").orElseThrow());
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertEquals("application/json", response.getHeaders().getFirst("content-type"));
        assertEquals("value", response.getHeaders().getFirst("x-kobo-test"));
        assertArrayEquals(responseBody, response.getBody());
    }
}
