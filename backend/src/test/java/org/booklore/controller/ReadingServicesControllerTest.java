package org.booklore.controller;

import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.kobo.KoboAnnotationRequest;
import org.booklore.service.kobo.KoboAnnotationSyncService;
import org.booklore.service.kobo.KoboServerProxy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReadingServicesControllerTest {

    @Mock
    private KoboServerProxy koboServerProxy;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private KoboAnnotationSyncService annotationSyncService;

    @InjectMocks
    private ReadingServicesController controller;

    @Test
    void patchAnnotations_persistsBeforeProxyingRegardlessOfUpstreamStatus() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        byte[] body = "{}".getBytes();
        BookLoreUser user = BookLoreUser.builder().id(7L).build();
        KoboAnnotationRequest annotationRequest = new KoboAnnotationRequest();
        when(koboServerProxy.proxyToReadingServices(request, body)).thenReturn(ResponseEntity.badRequest().body(body));
        when(objectMapper.readValue(body, KoboAnnotationRequest.class)).thenReturn(annotationRequest);

        controller.patchAnnotations("42", user, body, request);

        InOrder order = inOrder(objectMapper, annotationSyncService, koboServerProxy);
        order.verify(objectMapper).readValue(body, KoboAnnotationRequest.class);
        order.verify(annotationSyncService).syncAnnotations(42L, 7L, annotationRequest);
        order.verify(koboServerProxy).proxyToReadingServices(request, body);
    }

    @Test
    void patchAnnotations_stillProxiesWhenPersistenceFails() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        byte[] body = "{}".getBytes();
        BookLoreUser user = BookLoreUser.builder().id(7L).build();
        when(objectMapper.readValue(body, KoboAnnotationRequest.class)).thenThrow(new RuntimeException("invalid body"));
        when(koboServerProxy.proxyToReadingServices(request, body)).thenReturn(ResponseEntity.ok(body));

        controller.patchAnnotations("42", user, body, request);

        verifyNoInteractions(annotationSyncService);
        verify(koboServerProxy).proxyToReadingServices(request, body);
    }
}
