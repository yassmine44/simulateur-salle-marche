package tn.esprit.simulateurbackend;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import tn.esprit.simulateurbackend.security.RecaptchaService;
import tn.esprit.simulateurbackend.service.AuditService;

/*
 * Remplace RecaptchaService pendant les tests :
 * aucun appel à Google reCAPTCHA Enterprise.
 *
 * Le token reste obligatoire dans les requêtes
 * (validation @NotBlank des DTO).
 */
@Service
@Primary
class TestRecaptchaService extends RecaptchaService {

    TestRecaptchaService(
            AuditService auditService
    ) {
        super(auditService);
    }

    @Override
    public void verify(
            String token,
            String expectedAction,
            HttpServletRequest request
    ) {
        // Toujours accepté.
    }
}
