package tn.esprit.simulateurbackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Spring Boot 4 / webmvc does not always auto-expose RestClient.Builder.
 * Provide one explicitly for BankingScenarioProposalService, flash ML/voice, etc.
 */
@Configuration
public class RestClientConfig {

    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
