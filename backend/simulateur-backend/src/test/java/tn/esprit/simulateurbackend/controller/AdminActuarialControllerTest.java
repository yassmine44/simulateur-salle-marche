package tn.esprit.simulateurbackend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

import tn.esprit.simulateurbackend.dto.actuarial.PricingAssumptionsDto;
import tn.esprit.simulateurbackend.service.AdminActuarialService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminActuarialControllerTest {

    @Test
    void isRestrictedToAdministrators() {
        PreAuthorize rule = AdminActuarialController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(rule, "Le contrôleur d'administration doit exiger le rôle ADMIN.");
        assertEquals("hasRole('ADMIN')", rule.value());
    }

    @Test
    void passesTheAdministratorIdentityForTraceability() {
        AdminActuarialService service = mock(AdminActuarialService.class);
        AdminActuarialController controller = new AdminActuarialController(service);
        Authentication admin = mock(Authentication.class);
        when(admin.getName()).thenReturn("admin@example.test");
        PricingAssumptionsDto assumptions = PricingAssumptionsDto.of(0.2, 0.05, 0.02);

        controller.updateAssumptions(admin, assumptions);
        controller.reload(admin);
        controller.quotes(25);

        verify(service).updateAssumptions("admin@example.test", assumptions);
        verify(service).reload("admin@example.test");
        verify(service).recentQuotes(25);
    }
}
