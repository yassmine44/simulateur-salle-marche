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
         * Laisse d'abord Spring Security effectuer
         * le vrai traitement OpenID Connect :
         *
         * - échange authorization code
         * - validation ID Token
         * - récupération des claims
         */
        OidcUser oidcUser =
                delegate.loadUser(userRequest);


        // ==========================================
        // PROVIDER
        // ==========================================

        String registrationId =
                userRequest
                        .getClientRegistration()
                        .getRegistrationId();


        if (!"google".equalsIgnoreCase(
                registrationId
        )) {

            throw oauthError(
                    "unsupported_provider",
                    "Fournisseur OAuth non pris en charge."
            );
        }


        // ==========================================
        // GOOGLE SUBJECT (sub)
        // ==========================================

        String providerSubject =
                oidcUser.getSubject();


        if (
                providerSubject == null
                        || providerSubject.isBlank()
        ) {

            throw oauthError(
                    "missing_subject",
                    "Identifiant Google manquant."
            );
        }


        // ==========================================
        // EMAIL
        // ==========================================

        String rawEmail =
                oidcUser.getEmail();


        if (
                rawEmail == null
                        || rawEmail.isBlank()
        ) {

            throw oauthError(
                    "missing_email",
                    "Adresse e-mail Google manquante."
            );
        }


        String email =
                rawEmail
                        .trim()
                        .toLowerCase(
                                Locale.ROOT
                        );


        // ==========================================
        // EMAIL VERIFIED
        // ==========================================

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


        // ==========================================
        // FIND / CREATE APPLICATION USER
        // ==========================================

        User applicationUser =
                findOrCreateUser(
                        oidcUser,
                        providerSubject,
                        email
                );


        // ==========================================
        // DISABLED ACCOUNT
        // ==========================================

        if (!applicationUser.isEnabled()) {

            throw oauthError(
                    "account_disabled",
                    "Ce compte est désactivé."
            );
        }


        // ==========================================
        // APPLICATION ROLE
        // ==========================================

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
         * IMPORTANT :
         *
         * On utilise "email" comme attribut principal
         * du principal Spring.
         *
         * Ainsi :
         *
         * authentication.getName()
         *
         * retournera l'e-mail et restera compatible
         * avec le code existant :
         *
         * /api/auth/me
         * profile
         * admin
         * etc.
         */

        if (oidcUser.getUserInfo() != null) {

            return new DefaultOidcUser(
                    authorities,
                    oidcUser.getIdToken(),
                    oidcUser.getUserInfo(),
                    "email"
            );
        }


        return new DefaultOidcUser(
                authorities,
                oidcUser.getIdToken(),
                "email"
        );
    }


    // ==========================================
    // FIND OR CREATE USER
    // ==========================================

    private User findOrCreateUser(
            OidcUser oidcUser,
            String providerSubject,
            String email
    ) {

        /*
         * CAS 1 :
         *
         * L'identité Google existe déjà.
         */
        return userIdentityRepository
                .findByProviderAndProviderSubject(
                        AuthProvider.GOOGLE,
                        providerSubject
                )
                .map(identity -> {

                    User user =
                            identity.getUser();

                    /*
                     * On met éventuellement à jour
                     * l'e-mail observé chez Google.
                     */
                    identity.setEmailAtProvider(
                            email
                    );

                    userIdentityRepository.save(
                            identity
                    );

                    return user;
                })

                /*
                 * CAS 2 / CAS 3 :
                 *
                 * Pas encore d'identité Google.
                 */
                .orElseGet(() ->
                        linkOrCreateUser(
                                oidcUser,
                                providerSubject,
                                email
                        )
                );
    }


    // ==========================================
    // LINK EXISTING USER OR CREATE NEW USER
    // ==========================================

    private User linkOrCreateUser(
            OidcUser oidcUser,
            String providerSubject,
            String email
    ) {

        /*
         * CAS 2 :
         *
         * Un compte local avec le même email
         * existe déjà.
         *
         * Comme Google nous fournit un email
         * vérifié, on relie l'identité Google
         * à ce compte.
         */

        User user =
                userRepository
                        .findByEmailIgnoreCase(
                                email
                        )
                        .orElseGet(() ->
                                createGoogleUser(
                                        oidcUser,
                                        email
                                )
                        );


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
                AuthProvider.GOOGLE
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


    // ==========================================
    // CREATE GOOGLE USER
    // ==========================================

    private User createGoogleUser(
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


        User user =
                new User();


        /*
         * given_name n'est pas toujours garanti.
         */
        if (givenName != null) {

            user.setFirstName(
                    givenName
            );

        } else if (fullName != null) {

            user.setFirstName(
                    fullName
            );

        } else {

            user.setFirstName(
                    "Utilisateur"
            );
        }


        /*
         * family_name peut également être absent.
         */
        user.setLastName(
                familyName != null
                        ? familyName
                        : ""
        );


        user.setEmail(
                email
        );


        /*
         * IMPORTANT :
         *
         * Aucun faux mot de passe.
         */
        user.setPassword(
                null
        );


        /*
         * Google ne peut JAMAIS créer un ADMIN.
         */
        user.setRole(
                Role.USER
        );


        user.setEnabled(
                true
        );


        /*
         * Google ne fournit pas ces informations
         * dans notre flow actuel.
         */
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


    // ==========================================
    // HELPERS
    // ==========================================

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