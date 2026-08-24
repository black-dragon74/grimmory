package org.booklore.config.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.booklore.config.security.userdetails.UserAuthenticationDetails;
import org.booklore.mapper.custom.BookLoreUserTransformer;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.kobo.KoboHeaders;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.KoboUserSettingsEntity;
import org.booklore.repository.KoboUserSettingsRepository;
import org.booklore.repository.UserRepository;
import org.springframework.boot.web.servlet.FilterRegistration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
@FilterRegistration(enabled = false)
public class DeviceIDAuthFilter extends OncePerRequestFilter {

    private final KoboUserSettingsRepository koboUserSettingsRepository;
    private final UserRepository userRepository;
    private final BookLoreUserTransformer bookLoreUserTransformer;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String deviceId = request.getHeader(KoboHeaders.X_KOBO_DEVICEID);

        if (deviceId == null || deviceId.isBlank()) {
            log.warn("Reading services request missing {} header", KoboHeaders.X_KOBO_DEVICEID);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Device ID missing");
            return;
        }

        Optional<KoboUserSettingsEntity> settingsOpt = findSettingsForDeviceId(deviceId);
        if (settingsOpt.isEmpty()) {
            log.warn("Reading services request with unrecognized device ID");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid device ID");
            return;
        }

        KoboUserSettingsEntity settings = settingsOpt.get();
        Optional<BookLoreUserEntity> userOpt = userRepository.findByIdWithDetails(settings.getUserId());
        if (userOpt.isEmpty()) {
            log.warn("User not found for device ID");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "User not found");
            return;
        }

        BookLoreUserEntity entity = userOpt.get();
        if (entity.getPermissions() == null || !entity.getPermissions().isPermissionSyncKobo()) {
            log.warn("User {} does not have syncKobo permission", entity.getId());
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Insufficient permissions");
            return;
        }

        BookLoreUser user = bookLoreUserTransformer.toDTO(entity);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_DEVICE"))
        );
        authentication.setDetails(new UserAuthenticationDetails(request, user.getId()));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private Optional<KoboUserSettingsEntity> findSettingsForDeviceId(String deviceId) {
        List<KoboUserSettingsEntity> settingsWithDeviceIds = koboUserSettingsRepository.findByAllowedDeviceIdsIsNotNull();
        List<KoboUserSettingsEntity> matches = settingsWithDeviceIds.stream()
                .filter(settings -> Arrays.stream(settings.getAllowedDeviceIds().split(","))
                        .map(String::trim)
                        .filter(id -> !id.isEmpty())
                        .anyMatch(id -> id.equals(deviceId)))
                .limit(2)
                .toList();

        if (matches.size() > 1) {
            log.error("Kobo device ID is configured for multiple users");
            return Optional.empty();
        }
        return matches.stream().findFirst();
    }
}
