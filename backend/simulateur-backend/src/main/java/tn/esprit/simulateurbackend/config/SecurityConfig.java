package tn.esprit.simulateurbackend.config;

import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
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

import tn.esprit.simulateurbackend.entity.AuditEventType;
import tn.esprit.simulateurbackend.entity.User;
import tn.esprit.simulateurbackend.repository.UserRepository;
import tn.esprit.simulateurbackend.security.CustomOidcUserService;
import tn.esprit.simulateurbackend.service.AuditService;


@Configuration
public class SecurityConfig {

    private final CustomOidcUserService customOidcUserService;

    private final AuditService auditService;

    private final UserRepository userRepository;

    private final String frontendBaseUrl;


    public SecurityConfig(
            CustomOidcUserService customOidcUserService,
            AuditService auditService,
            UserRepository userRepository,
            @Value("${app.frontend.base-url}")
            String frontendBaseUrl
    ) {

        this.customOidcUserService =
                customOidcUserService;

        this.auditService =
                auditService;

        this.userRepository =
                userRepository;

        this.frontendBaseUrl =
                frontendBaseUrl;
    }


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

                        .spa()

                        .ignoringRequestMatchers(
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password"
                        )
                )


                // =========================
                // AUTHORIZATIONS
                // =========================
                .authorizeHttpRequests(auth -> auth

                        .requestMatchers(
                                HttpMethod.OPTIONS,
                                "/**"
                        )
                        .permitAll()


                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/health"
                        )
                        .permitAll()


                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password"
                        )
                        .permitAll()


                        .requestMatchers(
                                "/oauth2/**",
                                "/login/oauth2/**"
                        )
                        .permitAll()


                        .requestMatchers(
                                "/api/admin/**"
                        )
                        .hasRole("ADMIN")


                        .anyRequest()
                        .authenticated()
                )


                // =========================
                // GOOGLE OAuth2 / OIDC
                // =========================
                .oauth2Login(oauth -> oauth

                        .userInfoEndpoint(userInfo ->
                                userInfo
                                        .oidcUserService(
                                                customOidcUserService
                                        )
                        )


                        // =========================
                        // GOOGLE SUCCESS
                        // =========================
                        .successHandler(
                                (
                                        request,
                                        response,
                                        authentication
                                ) -> {

                                    String email =
                                            authentication
                                                    .getName()
                                                    .trim()
                                                    .toLowerCase(
                                                            Locale.ROOT
                                                    );


                                    User user =
                                            userRepository
                                                    .findByEmailIgnoreCase(
                                                            email
                                                    )
                                                    .orElse(null);


                                    auditService.log(
                                            AuditEventType.GOOGLE_LOGIN_SUCCESS,

                                            user != null
                                                    ? user.getId()
                                                    : null,

                                            email,

                                            email,

                                            true,

                                            "Connexion Google OAuth2/OIDC réussie.",

                                            request
                                    );


                                    response.sendRedirect(
                                            frontendBaseUrl
                                                    + "/auth/oauth2/callback"
                                    );
                                }
                        )


                        // =========================
                        // GOOGLE FAILURE
                        // =========================
                        .failureHandler(
                                (
                                        request,
                                        response,
                                        exception
                                ) -> {

                                    /*
                                     * On n'enregistre pas de token,
                                     * pas de credential,
                                     * pas de données sensibles.
                                     */
                                    auditService.log(
                                            AuditEventType.GOOGLE_LOGIN_FAILED,

                                            null,

                                            null,

                                            null,

                                            false,

                                            "Échec de connexion Google : "
                                                    + exception
                                                    .getClass()
                                                    .getSimpleName(),

                                            request
                                    );


                                    response.sendRedirect(
                                            frontendBaseUrl
                                                    + "/login?oauthError=google"
                                    );
                                }
                        )
                )


                // =========================
                // NO FORM LOGIN
                // =========================
                .formLogin(
                        form ->
                                form.disable()
                )


                // =========================
                // NO HTTP BASIC
                // =========================
                .httpBasic(
                        basic ->
                                basic.disable()
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

        return configuration
                .getAuthenticationManager();
    }


    // =========================
    // SECURITY CONTEXT SESSION
    // =========================

    @Bean
    SecurityContextRepository
    securityContextRepository() {

        return new HttpSessionSecurityContextRepository();
    }


    // =========================
    // CORS
    // =========================

    @Bean
    CorsConfigurationSource
    corsConfigurationSource() {

        CorsConfiguration configuration =
                new CorsConfiguration();


        configuration.setAllowedOrigins(
                List.of(
                        frontendBaseUrl
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


        configuration.setAllowCredentials(
                true
        );


        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();


        source.registerCorsConfiguration(
                "/**",
                configuration
        );


        return source;
    }
}