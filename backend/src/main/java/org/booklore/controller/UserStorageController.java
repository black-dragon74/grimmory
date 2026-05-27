package org.booklore.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.booklore.service.kobo.KoboServerProxy;
import org.booklore.util.RequestUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/UserStorage")
public class UserStorageController {

    private final KoboServerProxy koboServerProxy;

    @RequestMapping(value = "/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
            RequestMethod.DELETE, RequestMethod.PATCH})
    public ResponseEntity<byte[]> proxyUserStorage(HttpServletRequest request) {
        byte[] body = RequestUtils.readBody(request);
        return koboServerProxy.proxyToReadingServices(body);
    }
}
