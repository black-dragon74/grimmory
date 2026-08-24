package org.booklore.service.kobo;

import org.booklore.model.dto.kobo.KoboAnnotation;
import org.booklore.model.dto.kobo.KoboAnnotationRequest;
import org.booklore.model.entity.AnnotationEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.UserPermissionsEntity;
import org.booklore.repository.AnnotationRepository;
import org.booklore.repository.BookRepository;
import org.booklore.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KoboAnnotationSyncServiceTest {

    @Mock
    private AnnotationRepository annotationRepository;
    @Mock
    private BookRepository bookRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private KoboAnnotationSyncService service;

    private BookEntity book;
    private BookLoreUserEntity user;

    @BeforeEach
    void setUp() {
        LibraryEntity library = LibraryEntity.builder().id(3L).build();
        book = BookEntity.builder().id(42L).library(library).build();
        user = BookLoreUserEntity.builder()
                .id(7L)
                .permissions(UserPermissionsEntity.builder().build())
                .libraries(Set.of(library))
                .build();
    }

    @Test
    void createsBookScopedExternalAnnotation() {
        KoboAnnotation annotation = new KoboAnnotation();
        annotation.setId("annotation-id");
        annotation.setHighlightedText("highlighted text");
        KoboAnnotationRequest request = new KoboAnnotationRequest();
        request.setUpdatedAnnotations(List.of(annotation));
        when(bookRepository.findById(42L)).thenReturn(Optional.of(book));
        when(userRepository.findByIdWithDetails(7L)).thenReturn(Optional.of(user));
        when(annotationRepository.findByExternalIdAndBookIdAndUserId("annotation-id", 42L, 7L))
                .thenReturn(Optional.empty());

        service.syncAnnotations(42L, 7L, request);

        ArgumentCaptor<AnnotationEntity> captor = ArgumentCaptor.forClass(AnnotationEntity.class);
        verify(annotationRepository).save(captor.capture());
        AnnotationEntity saved = captor.getValue();
        assertEquals("kobo::annotation-id", saved.getCfi());
        assertEquals("annotation-id", saved.getExternalId());
        assertEquals("kobo", saved.getSource());
        assertEquals("highlighted text", saved.getText());
        assertSame(book, saved.getBook());
        assertSame(user, saved.getUser());
    }

    @Test
    void scopesDeletesToBookAndUser() {
        KoboAnnotationRequest request = new KoboAnnotationRequest();
        request.setDeletedAnnotationIds(List.of("one", "one", "two"));
        AnnotationEntity existing = AnnotationEntity.builder().id(9L).build();
        when(bookRepository.findById(42L)).thenReturn(Optional.of(book));
        when(userRepository.findByIdWithDetails(7L)).thenReturn(Optional.of(user));
        when(annotationRepository.findByExternalIdInAndBookIdAndUserId(
                List.of("one", "two"), 42L, 7L)).thenReturn(List.of(existing));

        service.syncAnnotations(42L, 7L, request);

        verify(annotationRepository).deleteAll(List.of(existing));
    }

    @Test
    void rejectsBookOutsideUsersLibraries() {
        user.setLibraries(Set.of());
        KoboAnnotationRequest request = new KoboAnnotationRequest();
        request.setDeletedAnnotationIds(List.of("one"));
        when(bookRepository.findById(42L)).thenReturn(Optional.of(book));
        when(userRepository.findByIdWithDetails(7L)).thenReturn(Optional.of(user));

        service.syncAnnotations(42L, 7L, request);

        verifyNoInteractions(annotationRepository);
    }
}
