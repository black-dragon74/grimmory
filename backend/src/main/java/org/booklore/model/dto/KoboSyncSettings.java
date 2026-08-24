package org.booklore.model.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class KoboSyncSettings {
    private Long id;
    private String userId;
    private String token;
    private boolean syncEnabled;
    private Float progressMarkAsReadingThreshold;
    private Float progressMarkAsFinishedThreshold;
    private boolean autoAddToShelf;
    private String hardcoverApiKey;
    private boolean hardcoverSyncEnabled;
    private boolean twoWayProgressSync;
    @Size(max = 4096)
    private String allowedDeviceIds;
}
