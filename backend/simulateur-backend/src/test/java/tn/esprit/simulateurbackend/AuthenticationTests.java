package tn.esprit.simulateurbackend;

import jakarta.servlet.http.HttpSession;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import tn.esprit.simulateurbackend.entity.Role;
import tn.esprit.simulateurbackend.entity.User;
import tn.esprit.simulateurbackend.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class AuthenticationTests {

    @Autowired
    WebApplicationContext context;

    @Autowired
    UserRepository users;

    @Autowired
    PasswordEncoder passwords;

    MockMvc mvc;

    @BeforeEach
    void setup() {

        users.deleteAll();

        mvc = MockMvcBuilders
                .webAppContextSetup(context)
                .build();
    }

    @Test
    void validCredentialsCreateAuthenticatedSession() throws Exception {

        createUser(true);

        var result = mvc.perform(
                        post("/api/auth/login")
                                .contentType("application/json")
                                .content("""
                                    {
                                      "email":"login.test@example.com",
                                      "password":"Password123!"
                                    }
                                    """)
                )
                .andExpect(status().isOk())

                .andExpect(
                        jsonPath("$.email")
                                .value("login.test@example.com")
                )

                .andExpect(
                        jsonPath("$.firstName")
                                .value("Login")
                )

                .andExpect(
                        jsonPath("$.lastName")
                                .value("Test")
                )

                .andExpect(
                        jsonPath("$.role")
                                .value("USER")
                )

                .andExpect(
                        jsonPath("$.password")
                                .doesNotExist()
                )

                .andReturn();


        HttpSession session =
                result.getRequest().getSession(false);

        assertThat(session).isNotNull();


        SecurityContext securityContext =
                (SecurityContext) session.getAttribute(
                        HttpSessionSecurityContextRepository
                                .SPRING_SECURITY_CONTEXT_KEY
                );

        assertThat(securityContext).isNotNull();

        assertThat(
                securityContext
                        .getAuthentication()
                        .isAuthenticated()
        ).isTrue();

        assertThat(
                securityContext
                        .getAuthentication()
                        .getName()
        ).isEqualTo("login.test@example.com");
    }


    @Test
    void loginEmailIsCaseInsensitive() throws Exception {

        createUser(true);

        mvc.perform(
                        post("/api/auth/login")
                                .contentType("application/json")
                                .content("""
                                    {
                                      "email":" LOGIN.TEST@EXAMPLE.COM ",
                                      "password":"Password123!"
                                    }
                                    """)
                )
                .andExpect(status().isOk())

                .andExpect(
                        jsonPath("$.email")
                                .value("login.test@example.com")
                );
    }


    @Test
    void wrongPasswordReturnsUnauthorized() throws Exception {

        createUser(true);

        mvc.perform(
                        post("/api/auth/login")
                                .contentType("application/json")
                                .content("""
                                    {
                                      "email":"login.test@example.com",
                                      "password":"WrongPassword123!"
                                    }
                                    """)
                )
                .andExpect(status().isUnauthorized());

    }


    @Test
    void unknownEmailReturnsUnauthorized() throws Exception {

        mvc.perform(
                        post("/api/auth/login")
                                .contentType("application/json")
                                .content("""
                                    {
                                      "email":"unknown@example.com",
                                      "password":"Password123!"
                                    }
                                    """)
                )
                .andExpect(status().isUnauthorized());

    }


    @Test
    void disabledUserCannotLogin() throws Exception {

        createUser(false);

        mvc.perform(
                        post("/api/auth/login")
                                .contentType("application/json")
                                .content("""
                                    {
                                      "email":"login.test@example.com",
                                      "password":"Password123!"
                                    }
                                    """)
                )
                .andExpect(status().isUnauthorized());

    }


    @Test
    void invalidLoginRequestReturnsBadRequest() throws Exception {

        mvc.perform(
                        post("/api/auth/login")
                                .contentType("application/json")
                                .content("""
                                    {
                                      "email":"invalid-email",
                                      "password":""
                                    }
                                    """)
                )
                .andExpect(status().isBadRequest());

    }


    private User createUser(boolean enabled) {

        User user = new User();

        user.setFirstName("Login");
        user.setLastName("Test");

        user.setEmail(
                "login.test@example.com"
        );

        user.setPassword(
                passwords.encode(
                        "Password123!"
                )
        );

        user.setCountryCode("TN");
        user.setPhoneNumber("+21622123456");

        user.setRole(Role.USER);
        user.setEnabled(enabled);

        return users.saveAndFlush(user);
    }
}