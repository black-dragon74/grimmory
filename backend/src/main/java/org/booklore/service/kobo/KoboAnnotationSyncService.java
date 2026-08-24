package org.booklore.service.kobo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.kobo.KoboAnnotation;
import org.booklore.model.dto.kobo.KoboAnnotationRequest;
import org.booklore.model.dto.kobo.KoboAnnotationSpan;
import org.booklore.model.entity.AnnotationEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.repository.AnnotationRepository;
import org.booklore.repository.BookRepository;
import org.booklore.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class KoboAnnotationSyncService {

    private static final String SOURCE_KOBO = "kobo";
    private static final String CFI_PREFIX = "kobo::";
    private static final int MAX_EXTERNAL_ID_LENGTH = 255;
    private static final int MAX_TEXT_LENGTH = 5000;
    private static final int MAX_CHAPTER_TITLE_LENGTH = 500;

    private final AnnotationRepository annotationRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;

    @Transactional
    public void syncAnnotations(Long bookId, Long userId, KoboAnnotationRequest request) {
        if (request == null || !hasChanges(request)) {
            return;
        }

        BookEntity book = bookRepository.findById(bookId).orElse(null);
        BookLoreUserEntity user = userRepository.findByIdWithDetails(userId).orElse(null);
        if (book == null || user == null || !canAccess(user, book)) {
            log.warn("Cannot sync Kobo annotations for book {} and user {}", bookId, userId);
            return;
        }

        List<KoboAnnotation> updated = request.getUpdatedAnnotations();
        if (updated != null) {
            for (KoboAnnotation koboAnnotation : updated) {
                upsertAnnotation(koboAnnotation, book, user);
            }
        }

        List<String> deleted = request.getDeletedAnnotationIds();
        if (deleted != null) {
            deleteAnnotations(deleted, bookId, userId);
        }
    }

    private boolean hasChanges(KoboAnnotationRequest request) {
        return request.getUpdatedAnnotations() != null && !request.getUpdatedAnnotations().isEmpty()
                || request.getDeletedAnnotationIds() != null && !request.getDeletedAnnotationIds().isEmpty();
    }

    private boolean canAccess(BookLoreUserEntity user, BookEntity book) {
        if (user.getPermissions() != null && user.getPermissions().isPermissionAdmin()) {
            return true;
        }
        if (book.getLibrary() == null || user.getLibraries() == null) {
            return false;
        }
        Long libraryId = book.getLibrary().getId();
        return user.getLibraries().stream()
                .map(LibraryEntity::getId)
                .anyMatch(id -> Objects.equals(id, libraryId));
    }

    private void upsertAnnotation(KoboAnnotation koboAnnotation, BookEntity book, BookLoreUserEntity user) {
        if (koboAnnotation == null) {
            return;
        }

        String annotationId = koboAnnotation.getId();
        if (!isValidAnnotationId(annotationId)) {
            log.debug("Skipping Kobo annotation with invalid ID");
            return;
        }

        String highlightedText = koboAnnotation.getHighlightedText();
        if (highlightedText == null || highlightedText.isBlank()) {
            log.debug("Skipping Kobo annotation {} with no highlighted text", koboAnnotation.getId());
            return;
        }

        KoboAnnotationSpan span = koboAnnotation.getLocation() != null
                ? koboAnnotation.getLocation().getSpan() : null;

        Optional<AnnotationEntity> existingOpt = annotationRepository.findByExternalIdAndBookIdAndUserId(
                annotationId, book.getId(), user.getId());

        if (existingOpt.isPresent()) {
            AnnotationEntity existing = existingOpt.get();
            existing.setText(truncate(highlightedText, MAX_TEXT_LENGTH));
            existing.setNote(truncate(koboAnnotation.getNoteText(), MAX_TEXT_LENGTH));
            if (koboAnnotation.getHighlightColor() != null) {
                existing.setColor(koboAnnotation.getHighlightColor());
            }
            if (span != null) {
                existing.setChapterTitle(truncate(span.getChapterTitle(), MAX_CHAPTER_TITLE_LENGTH));
            }
            annotationRepository.save(existing);
            log.debug("Updated Kobo annotation {}", koboAnnotation.getId());
        } else {
            AnnotationEntity annotation = AnnotationEntity.builder()
                    .cfi(CFI_PREFIX + annotationId)
                    .text(truncate(highlightedText, MAX_TEXT_LENGTH))
                    .color(koboAnnotation.getHighlightColor() != null ? koboAnnotation.getHighlightColor() : "#FFFF00")
                    .style("highlight")
                    .note(truncate(koboAnnotation.getNoteText(), MAX_TEXT_LENGTH))
                    .chapterTitle(span != null ? truncate(span.getChapterTitle(), MAX_CHAPTER_TITLE_LENGTH) : null)
                    .externalId(annotationId)
                    .source(SOURCE_KOBO)
                    .book(book)
                    .user(user)
                    .build();
            annotationRepository.save(annotation);
            log.debug("Created Kobo annotation {}", koboAnnotation.getId());
        }
    }

    private void deleteAnnotations(List<String> deletedIds, Long bookId, Long userId) {
        List<String> annotationIds = deletedIds.stream()
                .filter(this::isValidAnnotationId)
                .distinct()
                .toList();
        if (annotationIds.isEmpty()) {
            return;
        }

        List<AnnotationEntity> toDelete = annotationRepository.findByExternalIdInAndBookIdAndUserId(
                annotationIds, bookId, userId);
        if (!toDelete.isEmpty()) {
            annotationRepository.deleteAll(toDelete);
            log.debug("Deleted {} Kobo annotations", toDelete.size());
        }
    }

    private boolean isValidAnnotationId(String annotationId) {
        return annotationId != null && !annotationId.isBlank() && annotationId.length() <= MAX_EXTERNAL_ID_LENGTH;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen);
    }
}
