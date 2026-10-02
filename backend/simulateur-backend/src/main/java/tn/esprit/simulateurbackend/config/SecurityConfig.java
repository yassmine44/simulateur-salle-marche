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

import tn.esprit.simulateurbackend.security.CustomOidcUserService;

@Configuration
public class SecurityConfig {

    private final CustomOidcUserService customOidcUserService;

    public SecurityConfig(
            CustomOidcUserService customOidcUserService
    ) {
        this.customOidcUserService =
                customOidcUserService;
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

                        // Angular CORS preflight
                        .requestMatchers(
                                HttpMethod.OPTIONS,
                                "/**"
                        )
                        .permitAll()


                        // Health check
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/health"
                        )
                        .permitAll()


                        // Public authentication endpoints
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password"
                        )
                        .permitAll()


                        // OAuth2 / OIDC endpoints
                        .requestMatchers(
                                "/oauth2/**",
                                "/login/oauth2/**"
                        )
                        .permitAll()


                        // ADMIN only
                        .requestMatchers(
                                "/api/admin/**"
                        )
                        .hasRole("ADMIN")


                        // Everything else requires authentication
                        .anyRequest()
                        .authenticated()
                )


                // =========================
                // GOOGLE OAUTH2 / OIDC
                // =========================
                .oauth2Login(oauth -> oauth

                        .userInfoEndpoint(userInfo ->
                                userInfo
                                        .oidcUserService(
                                                customOidcUserService
                                        )
                        )

                        .successHandler(
                                (request, response, authentication) -> {

                                    response.sendRedirect(
                                            "http://localhost:4200/auth/oauth2/callback"
                                    );
                                }
                        )

                        .failureHandler(
                                (request, response, exception) -> {

                                    response.sendRedirect(
                                            "http://localhost:4200/login?oauthError=google"
                                    );
                                }
                        )
                )


                // =========================
                // No Spring form login
                // =========================
                .formLogin(
                        form -> form.disable()
                )


                // =========================
                // No HTTP Basic
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

        return configuration
                .getAuthenticationManager();
    }


    // =========================
    // SECURITY CONTEXT SESSION
    // =========================

    @Bean
    SecurityContextRepository securityContextRepository() {

        return new HttpSessionSecurityContextRepository();
    }


    // =========================
    // CORS
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