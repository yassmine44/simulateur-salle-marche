package tn.esprit.simulateurbackend.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http

                // =========================
                // CORS
                // =========================
                .cors(cors -> {
                })

                // =========================
                // CSRF
                // =========================
                .csrf(csrf -> csrf

                        // Configuration SPA Angular
                        .spa()

                        // Endpoints publics ne nécessitant pas de CSRF
                        .ignoringRequestMatchers(
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password"
                        )
                )

                // =========================
                // AUTORISATIONS
                // =========================
                .authorizeHttpRequests(auth -> auth

                        // -------------------------
                        // CORS preflight Angular
                        // -------------------------
                        .requestMatchers(
                                HttpMethod.OPTIONS,
                                "/**"
                        )
                        .permitAll()


                        // -------------------------
                        // Health check public
                        // -------------------------
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/health"
                        )
                        .permitAll()


                        // -------------------------
                        // Authentification publique
                        // -------------------------
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password"
                        )
                        .permitAll()


                        // -------------------------
                        // Administration
                        // ADMIN uniquement
                        // -------------------------
                        .requestMatchers(
                                "/api/admin/**"
                        )
                        .hasRole("ADMIN")


                        // -------------------------
                        // Toutes les autres routes
                        // nécessitent une session
                        // -------------------------
                        .anyRequest()
                        .authenticated()
                )

                // =========================
                // Pas de formulaire Spring
                // =========================
                .formLogin(
                        form -> form.disable()
                )

                // =========================
                // Pas de HTTP Basic
                // =========================
                .httpBasic(
                        basic -> basic.disable()
                );

        return http.build();
    }


    // =========================
    // AUTHENTICATION MANAGER
    // =========================

    @Bean
    AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration
    ) throws Exception {

        return configuration.getAuthenticationManager();
    }


    // =========================
    // SESSION SECURITY CONTEXT
    // =========================

    @Bean
    SecurityContextRepository securityContextRepository() {

        return new HttpSessionSecurityContextRepository();
    }


    // =========================
    // CORS CONFIGURATION
    // =========================

    @Bean
    CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration =
                new CorsConfiguration();

        configuration.setAllowedOrigins(
                List.of(
                        "http://localhost:4200"
                )
        );

        configuration.setAllowedMethods(
                List.of(
                        "GET",
                        "POST",
                        "PUT",
                        "PATCH",
                        "DELETE",
                        "OPTIONS"
                )
        );

        configuration.setAllowedHeaders(
                List.of("*")
        );

        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
                "/**",
                configuration
        );

        return source;
    }
}