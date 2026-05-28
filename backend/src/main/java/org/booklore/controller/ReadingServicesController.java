package org.booklore.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.kobo.KoboAnnotation;
import org.booklore.model.dto.kobo.KoboAnnotationRequest;
import org.booklore.model.dto.kobo.KoboAnnotationSpan;
import org.booklore.service.kobo.KoboAnnotationSyncService;
import org.booklore.service.kobo.KoboServerProxy;
import org.booklore.util.RequestUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v3")
public class ReadingServicesController {

    private final KoboServerProxy koboServerProxy;
    private final ObjectMapper objectMapper;
    private final KoboAnnotationSyncService koboAnnotationSyncService;
    private final AuthenticationService authenticationService;

    @PatchMapping("/content/{entitlementId}/annotations")
    public ResponseEntity<byte[]> patchAnnotations(
            @PathVariable String entitlementId,
            HttpServletRequest request) {

        byte[] rawBody = RequestUtils.readBody(request);

        try {
            KoboAnnotationRequest annotationRequest = objectMapper.readValue(rawBody, KoboAnnotationRequest.class);
            logAnnotationRequest(entitlementId, annotationRequest);
            persistAnnotations(entitlementId, annotationRequest);
        } catch (Exception e) {
            log.error("Error processing annotation request for entitlement {}", entitlementId, e);
        }

        return koboServerProxy.proxyToReadingServices(rawBody);
    }

    @RequestMapping(value = "/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
            RequestMethod.DELETE, RequestMethod.PATCH})
    public ResponseEntity<byte[]> catchAll(HttpServletRequest request) {
        byte[] rawBody = RequestUtils.readBody(request);
        return koboServerProxy.proxyToReadingServices(rawBody);
    }

    private void persistAnnotations(String entitlementId, KoboAnnotationRequest annotationRequest) {
        if (annotationRequest == null) {
            return;
        }
        try {
            Long bookId = Long.parseLong(entitlementId);
            BookLoreUser user = authenticationService.getAuthenticatedUser();
            if (user == null) {
                log.warn("Cannot persist Kobo annotations: unable to resolve user");
                return;
            }
            koboAnnotationSyncService.syncAnnotations(bookId, user.getId(), annotationRequest);
        } catch (NumberFormatException e) {
            log.warn("Invalid entitlement ID for annotation persistence: {}", entitlementId);
        } catch (Exception e) {
            log.error("Failed to persist Kobo annotations for entitlement {}", entitlementId, e);
        }
    }

    private void logAnnotationRequest(String entitlementId, KoboAnnotationRequest annotationRequest) {
        if (annotationRequest == null) {
            return;
        }

        List<KoboAnnotation> updated = annotationRequest.getUpdatedAnnotations();
        List<String> deleted = annotationRequest.getDeletedAnnotationIds();

        log.debug("PATCH annotations for entitlement: {} | updated: {} | deleted: {}",
                entitlementId,
                updated != null ? updated.size() : 0,
                deleted != null ? deleted.size() : 0);

        if (updated != null) {
            for (KoboAnnotation annotation : updated) {
                KoboAnnotationSpan span = annotation.getLocation() != null
                        ? annotation.getLocation().getSpan() : null;

                log.debug("Annotation id={} type={} color={} chapter={} progress={} | text={}{}",
                        annotation.getId(),
                        annotation.getType(),
                        annotation.getHighlightColor(),
                        span != null ? span.getChapterFilename() : "?",
                        span != null ? span.getChapterProgress() : "?",
                        truncate(annotation.getHighlightedText(), 100),
                        annotation.getNoteText() != null ? " | note=" + truncate(annotation.getNoteText(), 100) : "");
            }
        }

        if (deleted != null && !deleted.isEmpty()) {
            log.debug("Deleted annotation IDs: {}", deleted);
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "<null>";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }
}
