
package tn.esprit.simulateurbackend.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import tn.esprit.simulateurbackend.dto.PortfolioStressResponse;
import tn.esprit.simulateurbackend.service.PortfolioStressService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PortfolioStressControllerTest {

    private PortfolioStressService stressService;
    private PortfolioStressController controller;

    @BeforeEach
    void setUp() {

        stressService = mock(PortfolioStressService.class);
        controller = new PortfolioStressController(stressService);
    }

    @Test
    void shouldUseAuthenticatedUser() {

        Authentication authentication =
                mock(Authentication.class);

        PortfolioStressResponse response =
                mock(PortfolioStressResponse.class);

        when(authentication.isAuthenticated())
                .thenReturn(true);

        when(authentication.getName())
                .thenReturn("trader@example.test");

        when(stressService.getStress("trader@example.test"))
                .thenReturn(response);

        var result = controller.getMyStress(authentication);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(response, result.getBody());

        verify(stressService)
                .getStress("trader@example.test");
    }

    @Test
    void shouldRejectMissingAuthentication() {

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> controller.getMyStress(null)
                );

        assertEquals(
                HttpStatus.UNAUTHORIZED,
                exception.getStatusCode()
        );

        verifyNoInteractions(stressService);
    }

    @Test
    void shouldRejectAnonymousAuthentication() {

        AnonymousAuthenticationToken anonymous =
                mock(AnonymousAuthenticationToken.class);

        when(anonymous.isAuthenticated()).thenReturn(true);

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> controller.getMyStress(anonymous)
                );

        assertEquals(
                HttpStatus.UNAUTHORIZED,
                exception.getStatusCode()
        );

        verifyNoInteractions(stressService);
    }
}
