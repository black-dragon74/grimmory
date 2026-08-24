package org.booklore.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.booklore.service.kobo.KoboServerProxy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class ReadingServicesController {

    private final KoboServerProxy koboServerProxy;

    @RequestMapping({"/api/v3/**", "/api/UserStorage/**"})
    public ResponseEntity<byte[]> proxyReadingServices(
            @RequestBody(required = false) byte[] rawBody,
            HttpServletRequest request) {
        return koboServerProxy.proxyToReadingServices(request, rawBody);
    }
}
