package tn.esprit.simulateurbackend.service;

import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.security.web.context.SecurityContextRepository;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import tn.esprit.simulateurbackend.dto.LoginRequest;
import tn.esprit.simulateurbackend.dto.LoginResponse;

import tn.esprit.simulateurbackend.entity.AuditEventType;
import tn.esprit.simulateurbackend.entity.User;

import tn.esprit.simulateurbackend.repository.UserRepository;


@Service
public class AuthenticationService {

    private final AuthenticationManager authenticationManager;

    private final UserRepository users;

    private final SecurityContextRepository
            securityContextRepository;

    private final AuditService auditService;


    public AuthenticationService(
            AuthenticationManager authenticationManager,
            UserRepository users,
            SecurityContextRepository securityContextRepository,
            AuditService auditService
    ) {

        this.authenticationManager =
                authenticationManager;

        this.users =
                users;

        this.securityContextRepository =
                securityContextRepository;

        this.auditService =
                auditService;
    }


    public LoginResponse login(
            LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {

        String email =
                request.email()
                        .trim()
                        .toLowerCase(
                                Locale.ROOT
                        );


        try {

            /*
             * =========================================
             * AUTHENTICATION
             * =========================================
             */

            Authentication authentication =
                    authenticationManager.authenticate(

                            new UsernamePasswordAuthenticationToken(
                                    email,
                                    request.password()
                            )

                    );


            /*
             * Protection contre
             * la fixation de session.
             */
            if (
                    httpRequest.getSession(false)
                            != null
            ) {

                httpRequest.changeSessionId();
            }


            /*
             * =========================================
             * SECURITY CONTEXT
             * =========================================
             */

            SecurityContext securityContext =
                    SecurityContextHolder
                            .createEmptyContext();


            securityContext.setAuthentication(
                    authentication
            );


            SecurityContextHolder.setContext(
                    securityContext
            );


            securityContextRepository.saveContext(
                    securityContext,
                    httpRequest,
                    httpResponse
            );


            /*
             * =========================================
             * USER
             * =========================================
             */

            User user =
                    users.findByEmail(email)
                            .orElseThrow(
                                    this::invalidCredentials
                            );


            /*
             * =========================================
             * AUDIT : LOGIN SUCCESS
             * =========================================
             */

            auditService.log(
                    AuditEventType.LOGIN_SUCCESS,
                    user.getId(),
                    user.getEmail(),
                    user.getEmail(),
                    true,
                    "Connexion locale réussie.",
                    httpRequest
            );


            return LoginResponse.from(
                    user
            );


        } catch (AuthenticationException exception) {

            /*
             * Toujours nettoyer le contexte
             * lorsqu'une authentification échoue.
             */
            SecurityContextHolder.clearContext();


            /*
             * =========================================
             * AUDIT : LOGIN FAILED
             * =========================================
             *
             * On conserve l'e-mail tenté mais
             * jamais le mot de passe.
             */

            auditService.log(
                    AuditEventType.LOGIN_FAILED,
                    null,
                    email,
                    email,
                    false,
                    "Échec de connexion locale : identifiants invalides.",
                    httpRequest
            );


            throw invalidCredentials();
        }

    }


    public LoginResponse currentUser(
            Authentication authentication
    ) {

        if (
                authentication == null ||
                        !authentication.isAuthenticated()
        ) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentification requise."
            );
        }


        String email =
                authentication
                        .getName()
                        .trim()
                        .toLowerCase(
                                Locale.ROOT
                        );


        User user =
                users.findByEmail(email)
                        .orElseThrow(() ->

                                new ResponseStatusException(
                                        HttpStatus.UNAUTHORIZED,
                                        "Authentification requise."
                                )

                        );


        return LoginResponse.from(
                user
        );
    }


    private ResponseStatusException
    invalidCredentials() {

        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Adresse e-mail ou mot de passe incorrect."
        );
    }

}