package tn.esprit.simulateurbackend.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Connexion au moteur actuariel Python (ml-service, routes /api/actuarial/*).
 *
 * @param baseUrl        URL racine du ml-service
 * @param connectTimeout délai maximal d'établissement de la connexion
 * @param readTimeout    délai maximal de réponse (un devis expliqué prend ~0,5 s)
 */
@ConfigurationProperties(prefix = "app.actuarial.ml-service")
public record ActuarialServiceProperties(
        @DefaultValue("http://localhost:5000") String baseUrl,
        @DefaultValue("2s") Duration connectTimeout,
        @DefaultValue("10s") Duration readTimeout
) {

    public ActuarialServiceProperties {
        if (connectTimeout.isNegative() || connectTimeout.isZero()
                || readTimeout.isNegative() || readTimeout.isZero()) {
            throw new IllegalArgumentException("Les délais du service actuariel doivent être positifs.");
        }
    }

    public String rootUrl() {
        String root = baseUrl == null || baseUrl.isBlank() ? "http://localhost:5000" : baseUrl.trim();
        return root.endsWith("/") ? root.substring(0, root.length() - 1) : root;
    }
}
