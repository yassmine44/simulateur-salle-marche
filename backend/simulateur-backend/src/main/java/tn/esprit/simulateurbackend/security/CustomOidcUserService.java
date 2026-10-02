package tn.esprit.simulateurbackend.security;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;

import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.simulateurbackend.entity.AuthProvider;
import tn.esprit.simulateurbackend.entity.Role;
import tn.esprit.simulateurbackend.entity.User;
import tn.esprit.simulateurbackend.entity.UserIdentity;

import tn.esprit.simulateurbackend.repository.UserIdentityRepository;
import tn.esprit.simulateurbackend.repository.UserRepository;


@Service
public class CustomOidcUserService
        implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final OidcUserService delegate =
            new OidcUserService();

    private final UserRepository userRepository;

    private final UserIdentityRepository
            userIdentityRepository;


    public CustomOidcUserService(
            UserRepository userRepository,
            UserIdentityRepository userIdentityRepository
    ) {

        this.userRepository =
                userRepository;

        this.userIdentityRepository =
                userIdentityRepository;
    }


    @Override
    @Transactional
    public OidcUser loadUser(
            OidcUserRequest userRequest
    ) throws OAuth2AuthenticationException {

        /*
         * Spring effectue :
         *
         * - échange authorization code
         * - validation ID Token
         * - récupération des claims OIDC
         */
        OidcUser oidcUser =
                delegate.loadUser(userRequest);


        /*
         * ==========================================
         * PROVIDER
         * ==========================================
         */

        String registrationId =
                userRequest
                        .getClientRegistration()
                        .getRegistrationId()
                        .toLowerCase(Locale.ROOT);


        AuthProvider provider =
                resolveProvider(registrationId);


        /*
         * ==========================================
         * PROVIDER SUBJECT
         * ==========================================
         */

        String providerSubject =
                resolveProviderSubject(
                        provider,
                        oidcUser
                );


        /*
         * ==========================================
         * EMAIL
         * ==========================================
         */

        String email =
                resolveEmail(
                        provider,
                        oidcUser
                );


        /*
         * ==========================================
         * PROVIDER-SPECIFIC VALIDATION
         * ==========================================
         */

        validateProviderClaims(
                provider,
                oidcUser
        );


        /*
         * ==========================================
         * APPLICATION USER
         * ==========================================
         */

        User applicationUser =
                findOrCreateUser(
                        provider,
                        oidcUser,
                        providerSubject,
                        email
                );


        if (!applicationUser.isEnabled()) {

            throw oauthError(
                    "account_disabled",
                    "Ce compte est désactivé."
            );
        }


        /*
         * L'application utilise actuellement
         * authentication.getName() comme e-mail.
         *
         * On évite donc qu'un changement d'e-mail
         * chez le fournisseur casse la session.
         */
        if (
                applicationUser.getEmail() == null
                        || !applicationUser
                        .getEmail()
                        .equalsIgnoreCase(email)
        ) {

            throw oauthError(
                    "email_mismatch",
                    "L'adresse e-mail du fournisseur ne correspond plus au compte associé."
            );
        }


        /*
         * ==========================================
         * AUTHORITIES
         * ==========================================
         */

        Set<GrantedAuthority> authorities =
                new HashSet<>(
                        oidcUser.getAuthorities()
                );


        authorities.add(
                new SimpleGrantedAuthority(
                        "ROLE_"
                                + applicationUser
                                .getRole()
                                .name()
                )
        );


        /*
         * ==========================================
         * PRINCIPAL NAME
         * ==========================================
         *
         * Notre application recherche ensuite
         * l'utilisateur avec :
         *
         * authentication.getName()
         *
         * Il faut donc que ce nom corresponde
         * à l'e-mail stocké dans users.
         */

        String nameAttributeKey =
                resolveNameAttributeKey(
                        provider,
                        oidcUser
                );


        if (oidcUser.getUserInfo() != null) {

            return new DefaultOidcUser(
                    authorities,
                    oidcUser.getIdToken(),
                    oidcUser.getUserInfo(),
                    nameAttributeKey
            );
        }


        return new DefaultOidcUser(
                authorities,
                oidcUser.getIdToken(),
                nameAttributeKey
        );
    }


    /*
     * ==========================================
     * PROVIDER
     * ==========================================
     */

    private AuthProvider resolveProvider(
            String registrationId
    ) {

        return switch (registrationId) {

            case "google" ->
                    AuthProvider.GOOGLE;

            case "microsoft" ->
                    AuthProvider.MICROSOFT;

            default ->
                    throw oauthError(
                            "unsupported_provider",
                            "Fournisseur OAuth non pris en charge."
                    );
        };
    }


    /*
     * ==========================================
     * PROVIDER SUBJECT
     * ==========================================
     */

    private String resolveProviderSubject(
            AuthProvider provider,
            OidcUser oidcUser
    ) {

        if (provider == AuthProvider.GOOGLE) {

            String subject =
                    clean(
                            oidcUser.getSubject()
                    );

            if (subject == null) {

                throw oauthError(
                        "missing_subject",
                        "Identifiant Google manquant."
                );
            }

            return subject;
        }


        /*
         * Microsoft :
         *
         * oid = identifiant objet utilisateur
         * tid = identifiant du tenant
         *
         * La combinaison tid + oid constitue
         * une identité Microsoft robuste.
         */

        String tenantId =
                clean(
                        oidcUser.getClaimAsString(
                                "tid"
                        )
                );

        String objectId =
                clean(
                        oidcUser.getClaimAsString(
                                "oid"
                        )
                );


        if (
                tenantId != null
                        && objectId != null
        ) {

            return tenantId + ":" + objectId;
        }


        /*
         * Fallback OIDC standard.
         */
        String subject =
                clean(
                        oidcUser.getSubject()
                );


        if (subject == null) {

            throw oauthError(
                    "missing_subject",
                    "Identifiant Microsoft manquant."
            );
        }


        return subject;
    }


    /*
     * ==========================================
     * EMAIL
     * ==========================================
     */

    private String resolveEmail(
            AuthProvider provider,
            OidcUser oidcUser
    ) {

        String rawEmail = null;


        /*
         * Google fournit normalement "email".
         */
        if (provider == AuthProvider.GOOGLE) {

            rawEmail =
                    clean(
                            oidcUser.getEmail()
                    );
        }


        /*
         * Microsoft :
         *
         * "email" n'est pas garanti.
         *
         * preferred_username est donc utilisé
         * comme fallback d'affichage/login.
         */
        if (provider == AuthProvider.MICROSOFT) {

            rawEmail =
                    clean(
                            oidcUser.getEmail()
                    );


            if (rawEmail == null) {

                rawEmail =
                        clean(
                                oidcUser.getClaimAsString(
                                        "preferred_username"
                                )
                        );
            }
        }


        if (
                rawEmail == null
                        || !rawEmail.contains("@")
        ) {

            throw oauthError(
                    "missing_email",
                    "Aucune adresse e-mail exploitable n'a été fournie."
            );
        }


        return rawEmail
                .trim()
                .toLowerCase(
                        Locale.ROOT
                );
    }


    /*
     * ==========================================
     * CLAIM VALIDATION
     * ==========================================
     */

    private void validateProviderClaims(
            AuthProvider provider,
            OidcUser oidcUser
    ) {

        /*
         * Google fournit email_verified.
         */
        if (provider == AuthProvider.GOOGLE) {

            Boolean emailVerified =
                    oidcUser.getClaimAsBoolean(
                            "email_verified"
                    );


            if (!Boolean.TRUE.equals(
                    emailVerified
            )) {

                throw oauthError(
                        "email_not_verified",
                        "L'adresse e-mail Google n'est pas vérifiée."
                );
            }
        }


        /*
         * Microsoft ne fournit pas de claim
         * standard email_verified équivalent
         * utilisable comme celui de Google.
         *
         * On ne fabrique donc pas cette validation.
         */
    }


    /*
     * ==========================================
     * FIND / CREATE USER
     * ==========================================
     */

    private User findOrCreateUser(
            AuthProvider provider,
            OidcUser oidcUser,
            String providerSubject,
            String email
    ) {

        return userIdentityRepository
                .findByProviderAndProviderSubject(
                        provider,
                        providerSubject
                )
                .map(identity -> {

                    User user =
                            identity.getUser();


                    if (
                            user.getEmail() == null
                                    || !user
                                    .getEmail()
                                    .equalsIgnoreCase(email)
                    ) {

                        throw oauthError(
                                "email_mismatch",
                                "L'adresse e-mail du fournisseur a changé."
                        );
                    }


                    identity.setEmailAtProvider(
                            email
                    );

                    userIdentityRepository.save(
                            identity
                    );


                    return user;
                })

                .orElseGet(() ->
                        linkOrCreateUser(
                                provider,
                                oidcUser,
                                providerSubject,
                                email
                        )
                );
    }


    /*
     * ==========================================
     * LINK / CREATE
     * ==========================================
     */

    private User linkOrCreateUser(
            AuthProvider provider,
            OidcUser oidcUser,
            String providerSubject,
            String email
    ) {

        User user;


        /*
         * GOOGLE
         *
         * Google nous fournit email_verified=true.
         *
         * On peut donc relier un compte local
         * existant ayant exactement le même e-mail.
         */
        if (provider == AuthProvider.GOOGLE) {

            user =
                    userRepository
                            .findByEmailIgnoreCase(
                                    email
                            )
                            .orElseGet(() ->
                                    createSocialUser(
                                            oidcUser,
                                            email
                                    )
                            );

        } else {

            /*
             * MICROSOFT
             *
             * Microsoft ne fournit pas le même
             * mécanisme email_verified que Google.
             *
             * Pour éviter une liaison automatique
             * dangereuse sur simple égalité d'e-mail,
             * on refuse si un compte existe déjà.
             */

            var existingUser =
                    userRepository
                            .findByEmailIgnoreCase(
                                    email
                            );


            if (existingUser.isPresent()) {

                throw oauthError(
                        "email_already_registered",
                        "Un compte existe déjà avec cette adresse e-mail. Connectez-vous d'abord avec votre méthode existante."
                );
            }


            user =
                    createSocialUser(
                            oidcUser,
                            email
                    );
        }


        if (!user.isEnabled()) {

            throw oauthError(
                    "account_disabled",
                    "Ce compte est désactivé."
            );
        }


        UserIdentity identity =
                new UserIdentity();


        identity.setUser(
                user
        );

        identity.setProvider(
                provider
        );

        identity.setProviderSubject(
                providerSubject
        );

        identity.setEmailAtProvider(
                email
        );


        userIdentityRepository.save(
                identity
        );


        return user;
    }


    /*
     * ==========================================
     * CREATE SOCIAL USER
     * ==========================================
     */

    private User createSocialUser(
            OidcUser oidcUser,
            String email
    ) {

        String givenName =
                clean(
                        oidcUser.getGivenName()
                );


        String familyName =
                clean(
                        oidcUser.getFamilyName()
                );


        String fullName =
                clean(
                        oidcUser.getFullName()
                );


        /*
         * Certains providers ne fournissent
         * pas given_name / family_name.
         */
        if (
                givenName == null
                        && fullName != null
        ) {

            String[] parts =
                    fullName.split(
                            "\\s+",
                            2
                    );


            givenName =
                    clean(
                            parts[0]
                    );


            if (
                    familyName == null
                            && parts.length > 1
            ) {

                familyName =
                        clean(
                                parts[1]
                        );
            }
        }


        User user =
                new User();


        user.setFirstName(
                givenName != null
                        ? givenName
                        : "Utilisateur"
        );


        user.setLastName(
                familyName != null
                        ? familyName
                        : ""
        );


        user.setEmail(
                email
        );


        /*
         * Compte social :
         *
         * aucun faux mot de passe.
         */
        user.setPassword(
                null
        );


        /*
         * Un provider externe ne peut
         * jamais attribuer ADMIN.
         */
        user.setRole(
                Role.USER
        );


        user.setEnabled(
                true
        );


        user.setCountryCode(
                null
        );


        user.setPhoneNumber(
                null
        );


        return userRepository.save(
                user
        );
    }


    /*
     * ==========================================
     * PRINCIPAL NAME ATTRIBUTE
     * ==========================================
     */

    private String resolveNameAttributeKey(
            AuthProvider provider,
            OidcUser oidcUser
    ) {

        if (provider == AuthProvider.GOOGLE) {

            return "email";
        }


        /*
         * Microsoft possède parfois "email".
         */
        String emailClaim =
                clean(
                        oidcUser.getClaimAsString(
                                "email"
                        )
                );


        if (
                emailClaim != null
                        && emailClaim.contains("@")
        ) {

            return "email";
        }


        /*
         * Sinon notre resolveEmail() a utilisé
         * preferred_username.
         */
        return "preferred_username";
    }


    /*
     * ==========================================
     * HELPERS
     * ==========================================
     */

    private String clean(
            String value
    ) {

        if (
                value == null
                        || value.isBlank()
        ) {

            return null;
        }


        return value.trim();
    }


    private OAuth2AuthenticationException oauthError(
            String code,
            String description
    ) {

        OAuth2Error error =
                new OAuth2Error(
                        code,
                        description,
                        null
                );


        return new OAuth2AuthenticationException(
                error,
                description
        );
    }
}