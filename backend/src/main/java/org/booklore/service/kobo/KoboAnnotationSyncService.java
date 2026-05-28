package org.booklore.service.kobo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.kobo.KoboAnnotation;
import org.booklore.model.dto.kobo.KoboAnnotationRequest;
import org.booklore.model.dto.kobo.KoboAnnotationSpan;
import org.booklore.model.entity.AnnotationEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.repository.AnnotationRepository;
import org.booklore.repository.BookRepository;
import org.booklore.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class KoboAnnotationSyncService {

    private static final String SOURCE_KOBO = "kobo";
    private static final String CFI_PREFIX = "kobo::";
    private static final int MAX_TEXT_LENGTH = 5000;
    private static final int MAX_CHAPTER_TITLE_LENGTH = 500;

    private final AnnotationRepository annotationRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;

    @Transactional
    public void syncAnnotations(Long bookId, Long userId, KoboAnnotationRequest request) {
        if (request == null) {
            return;
        }

        List<KoboAnnotation> updated = request.getUpdatedAnnotations();
        if (updated != null && !updated.isEmpty()) {
            BookEntity book = bookRepository.findById(bookId).orElse(null);
            BookLoreUserEntity user = userRepository.findById(userId).orElse(null);
            if (book == null || user == null) {
                log.warn("Cannot sync Kobo annotations: book {} or user {} not found", bookId, userId);
                return;
            }
            for (KoboAnnotation koboAnnotation : updated) {
                upsertAnnotation(koboAnnotation, book, user);
            }
        }

        List<String> deleted = request.getDeletedAnnotationIds();
        if (deleted != null && !deleted.isEmpty()) {
            deleteAnnotations(deleted, userId);
        }
    }

    private void upsertAnnotation(KoboAnnotation koboAnnotation, BookEntity book, BookLoreUserEntity user) {
        if (koboAnnotation.getId() == null || koboAnnotation.getId().isBlank()) {
            log.debug("Skipping Kobo annotation with no ID");
            return;
        }

        String highlightedText = koboAnnotation.getHighlightedText();
        if (highlightedText == null || highlightedText.isBlank()) {
            log.debug("Skipping Kobo annotation {} with no highlighted text", koboAnnotation.getId());
            return;
        }

        KoboAnnotationSpan span = koboAnnotation.getLocation() != null
                ? koboAnnotation.getLocation().getSpan() : null;

        Optional<AnnotationEntity> existingOpt = annotationRepository.findByExternalIdAndUserId(
                koboAnnotation.getId(), user.getId());

        if (existingOpt.isPresent()) {
            AnnotationEntity existing = existingOpt.get();
            existing.setText(truncate(highlightedText, MAX_TEXT_LENGTH));
            existing.setNote(truncate(koboAnnotation.getNoteText(), MAX_TEXT_LENGTH));
            existing.setColor(koboAnnotation.getHighlightColor());
            if (span != null) {
                existing.setChapterTitle(truncate(span.getChapterTitle(), MAX_CHAPTER_TITLE_LENGTH));
            }
            annotationRepository.save(existing);
            log.debug("Updated Kobo annotation {}", koboAnnotation.getId());
        } else {
            AnnotationEntity annotation = AnnotationEntity.builder()
                    .cfi(CFI_PREFIX + koboAnnotation.getId())
                    .text(truncate(highlightedText, MAX_TEXT_LENGTH))
                    .color(koboAnnotation.getHighlightColor() != null ? koboAnnotation.getHighlightColor() : "#FFFF00")
                    .style("highlight")
                    .note(truncate(koboAnnotation.getNoteText(), MAX_TEXT_LENGTH))
                    .chapterTitle(span != null ? truncate(span.getChapterTitle(), MAX_CHAPTER_TITLE_LENGTH) : null)
                    .externalId(koboAnnotation.getId())
                    .source(SOURCE_KOBO)
                    .book(book)
                    .user(user)
                    .build();
            annotationRepository.save(annotation);
            log.debug("Created Kobo annotation {}", koboAnnotation.getId());
        }
    }

    private void deleteAnnotations(List<String> deletedIds, Long userId) {
        List<AnnotationEntity> toDelete = annotationRepository.findByExternalIdInAndUserId(deletedIds, userId);
        if (!toDelete.isEmpty()) {
            annotationRepository.deleteAll(toDelete);
            log.debug("Deleted {} Kobo annotations", toDelete.size());
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen);
    }
}
