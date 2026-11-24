package tn.esprit.simulateurbackend.controller;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import tn.esprit.simulateurbackend.dto.actuarial.ComparisonRequest;
import tn.esprit.simulateurbackend.dto.actuarial.QuoteRequest;
import tn.esprit.simulateurbackend.dto.actuarial.QuoteResponse;
import tn.esprit.simulateurbackend.service.ActuarialFixtures;
import tn.esprit.simulateurbackend.service.ActuarialService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ActuarialControllerTest {

    private ActuarialService service;
    private ActuarialController controller;
    private Authentication user;

    @BeforeEach
    void setUp() {
        service = mock(ActuarialService.class);
        controller = new ActuarialController(service);
        user = mock(Authentication.class);
        when(user.isAuthenticated()).thenReturn(true);
        when(user.getName()).thenReturn("trader@example.test");
    }

    @Test
    void shouldQuoteForTheAuthenticatedUser() {
        QuoteRequest request = ActuarialFixtures.quoteRequest();
        QuoteResponse response = ActuarialFixtures.quoteResponse();
        when(service.quote("trader@example.test", request)).thenReturn(response);

        var result = controller.quote(user, request);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(response, result.getBody());
    }

    @Test
    void shouldReturnHistoryOfTheAuthenticatedUser() {
        when(service.history("trader@example.test", 5)).thenReturn(List.of());

        var result = controller.myQuotes(user, 5);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        verify(service).history("trader@example.test", 5);
    }

    @Test
    void shouldRejectMissingAuthenticationOnEveryEndpoint() {
        ComparisonRequest comparison = new ComparisonRequest(
                ActuarialFixtures.youngDriver(), ActuarialFixtures.experiencedDriver()
        );
        List<Runnable> calls = List.of(
                () -> controller.metadata(null),
                () -> controller.status(null),
                () -> controller.quote(null, ActuarialFixtures.quoteRequest()),
                () -> controller.myQuotes(null, 20),
                () -> controller.compare(null, comparison),
                () -> controller.report(null, "pricing"),
                () -> controller.modelCards(null)
        );

        for (Runnable call : calls) {
            ResponseStatusException error = assertThrows(ResponseStatusException.class, call::run);
            assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
        }
        verifyNoInteractions(service);
    }

    @Test
    void shouldRejectAnonymousAuthentication() {
        AnonymousAuthenticationToken anonymous = mock(AnonymousAuthenticationToken.class);
        when(anonymous.isAuthenticated()).thenReturn(true);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.quote(anonymous, ActuarialFixtures.quoteRequest())
        );

        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
        verifyNoInteractions(service);
    }
}
