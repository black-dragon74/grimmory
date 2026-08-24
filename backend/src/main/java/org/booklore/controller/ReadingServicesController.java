package org.booklore.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.kobo.KoboAnnotationRequest;
import org.booklore.service.kobo.KoboAnnotationSyncService;
import org.booklore.service.kobo.KoboServerProxy;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@RequiredArgsConstructor
@RestController
public class ReadingServicesController {

    private final KoboServerProxy koboServerProxy;
    private final ObjectMapper objectMapper;
    private final KoboAnnotationSyncService koboAnnotationSyncService;

    @PatchMapping("/api/v3/content/{entitlementId}/annotations")
    public ResponseEntity<byte[]> patchAnnotations(
            @PathVariable String entitlementId,
            @AuthenticationPrincipal BookLoreUser user,
            @RequestBody(required = false) byte[] rawBody,
            HttpServletRequest request) {

        persistAnnotations(entitlementId, user, rawBody);
        return koboServerProxy.proxyToReadingServices(request, rawBody);
    }

    @RequestMapping({"/api/v3/**", "/api/UserStorage/**"})
    public ResponseEntity<byte[]> catchAll(
            @RequestBody(required = false) byte[] rawBody,
            HttpServletRequest request) {
        return koboServerProxy.proxyToReadingServices(request, rawBody);
    }

    private void persistAnnotations(String entitlementId, BookLoreUser user, byte[] rawBody) {
        if (user == null || rawBody == null || rawBody.length == 0) {
            return;
        }
        try {
            Long bookId = Long.parseLong(entitlementId);
            KoboAnnotationRequest annotationRequest = objectMapper.readValue(rawBody, KoboAnnotationRequest.class);
            koboAnnotationSyncService.syncAnnotations(bookId, user.getId(), annotationRequest);
        } catch (NumberFormatException e) {
            log.warn("Invalid entitlement ID for annotation persistence: {}", entitlementId);
        } catch (Exception e) {
            log.error("Failed to persist Kobo annotations for entitlement {}", entitlementId, e);
        }
    }
}
