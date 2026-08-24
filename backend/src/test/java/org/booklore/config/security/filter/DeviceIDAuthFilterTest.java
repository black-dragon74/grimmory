package org.booklore.config.security.filter;

import jakarta.servlet.FilterChain;
import org.booklore.mapper.custom.BookLoreUserTransformer;
import org.booklore.model.dto.kobo.KoboHeaders;
import org.booklore.model.entity.KoboUserSettingsEntity;
import org.booklore.repository.KoboUserSettingsRepository;
import org.booklore.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceIDAuthFilterTest {

    @Mock
    private KoboUserSettingsRepository settingsRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private BookLoreUserTransformer userTransformer;
    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private DeviceIDAuthFilter filter;

    @Test
    void rejectsDeviceIdConfiguredForMultipleUsers() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(KoboHeaders.X_KOBO_DEVICEID, "shared-device");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(settingsRepository.findByAllowedDeviceIdsIsNotNull()).thenReturn(List.of(
                KoboUserSettingsEntity.builder().userId(1L).allowedDeviceIds("shared-device").build(),
                KoboUserSettingsEntity.builder().userId(2L).allowedDeviceIds("other, shared-device").build()));

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        verify(userRepository, never()).findByIdWithDetails(1L);
        verify(filterChain, never()).doFilter(request, response);
    }
}
