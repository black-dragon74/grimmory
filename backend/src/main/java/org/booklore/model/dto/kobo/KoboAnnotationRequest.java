package org.booklore.model.dto.kobo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class KoboAnnotationRequest {
    private List<KoboAnnotation> updatedAnnotations;
    private List<String> deletedAnnotationIds;
}
