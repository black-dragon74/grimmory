package org.booklore.model.dto.kobo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class KoboAnnotationSpan {
    private String chapterFilename;
    private Double chapterProgress;
    private String chapterTitle;
    private String startPath;
    private String endPath;
    private Integer startChar;
    private Integer endChar;
}
