package tn.esprit.simulateurbackend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tn.esprit.simulateurbackend.entity.Role;
import tn.esprit.simulateurbackend.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class RegistrationTests {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    MockMvc mvc;

    private static final String VALID = """
    {
      "firstName":"  Test  ",
      "lastName":" User ",
      "countryCode":"TN",
      "phoneNumber":"22 123 456",
      "email":" TEST@example.com ",
      "password":"StrongPassword123!"
    }
    """;
    @BeforeEach
    void setup() {
        users.deleteAll();
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test

    void registrationPersistsNormalizedUserAndHash() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(VALID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.firstName").value("Test"))
                .andExpect(jsonPath("$.lastName").value("User"))
                .andExpect(jsonPath("$.countryCode").value("TN"))
                .andExpect(jsonPath("$.phoneNumber").value("+21622123456"))
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        var user = users.findByEmail("test@example.com").orElseThrow();

        assertThat(user.getFirstName()).isEqualTo("Test");
        assertThat(user.getLastName()).isEqualTo("User");
        assertThat(user.getCountryCode()).isEqualTo("TN");
        assertThat(user.getPhoneNumber()).isEqualTo("+21622123456");
        assertThat(user.getRole()).isEqualTo(Role.USER);
        assertThat(user.isEnabled()).isTrue();
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getPassword()).isNotEqualTo("StrongPassword123!");
        assertThat(passwords.matches(
                "StrongPassword123!",
                user.getPassword()
        )).isTrue();
    }
    @Test
    void rejectsDuplicateRegardlessOfEmailCase() throws Exception {
        mvc.perform(post("/api/auth/register").contentType("application/json").content(VALID))
            .andExpect(status().isCreated());
        mvc.perform(post("/api/auth/register").contentType("application/json")
                .content(VALID.replace(" TEST@example.com ", "test@example.com")))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
        assertThat(users.count()).isEqualTo(1);
    }

 
    @Test
    void missingFieldsAndMalformedJsonAreRejected() throws Exception {
        mvc.perform(post("/api/auth/register").contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").contentType("application/json").content("{"))
            .andExpect(status().isBadRequest());
        assertThat(users.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "TRADER", "SALES", "SALES_TRADER", "RISK_MANAGER"})
    void clientCannotChooseRoleOrDisableAccount(String role) throws Exception {
        mvc.perform(post("/api/auth/register").contentType("application/json")
                .content(VALID.replace("}", ",\"role\":\"" + role + "\",\"enabled\":false}")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.role").value("USER"))
            .andExpect(jsonPath("$.enabled").value(true));
        var user = users.findByEmail("test@example.com").orElseThrow();
        assertThat(user.getRole()).isEqualTo(Role.USER);
        assertThat(user.isEnabled()).isTrue();
    }

    @Test
    void enforcesEightCharacterMinimum() throws Exception {
        mvc.perform(post("/api/auth/register").contentType("application/json")
                .content(VALID.replace("StrongPassword123!", "1234567")))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").contentType("application/json")
                .content(VALID.replace("StrongPassword123!", "12345678")))
            .andExpect(status().isCreated());
        assertThat(passwords.matches("12345678", users.findByEmail("test@example.com").orElseThrow().getPassword())).isTrue();
    }

    @Test
    void rejectsPasswordsAboveBcryptByteLimit() throws Exception {
        mvc.perform(post("/api/auth/register").contentType("application/json")
                .content(VALID.replace("StrongPassword123!", "é".repeat(40))))
            .andExpect(status().isBadRequest());
        assertThat(users.count()).isZero();
    }
    @Test
    void normalizesLowercaseCountryCode() throws Exception {
        String request = VALID.replace(
                "\"countryCode\":\"TN\"",
                "\"countryCode\":\"tn\""
        );

        mvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.countryCode").value("TN"))
                .andExpect(jsonPath("$.phoneNumber").value("+21622123456"));

        var user = users.findByEmail("test@example.com").orElseThrow();

        assertThat(user.getCountryCode()).isEqualTo("TN");
        assertThat(user.getPhoneNumber()).isEqualTo("+21622123456");
    }
    @Test
    void invalidFieldsDoNotCreateUserOrEchoPassword() throws Exception {
        var result = mvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("""
                {
                  "firstName":" ",
                  "lastName":" ",
                  "countryCode":"",
                  "phoneNumber":"",
                  "email":"invalid",
                  "password":"short"
                }
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.firstName").exists())
                .andExpect(jsonPath("$.errors.lastName").exists())
                .andExpect(jsonPath("$.errors.countryCode").exists())
                .andExpect(jsonPath("$.errors.phoneNumber").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("short");

        assertThat(users.count()).isZero();
    }
    @Test
    void rejectsUnsupportedCountryCode() throws Exception {
        String request = VALID.replace(
                "\"countryCode\":\"TN\"",
                "\"countryCode\":\"XX\""
        );

        mvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(request))
                .andExpect(status().isBadRequest());

        assertThat(users.count()).isZero();
    }
    @Test
    void rejectsPhoneNumberInvalidForSelectedCountry() throws Exception {
        String request = VALID.replace(
                "\"phoneNumber\":\"22 123 456\"",
                "\"phoneNumber\":\"123\""
        );

        mvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(request))
                .andExpect(status().isBadRequest());

        assertThat(users.count()).isZero();
    }

    @Test
    void angularCorsPreflightIsAllowed() throws Exception {
        mvc.perform(options("/api/auth/register").header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
    }

    @Test
    void healthEndpointStillWorksWithAngular() throws Exception {
        mvc.perform(get("/api/health").header("Origin", "http://localhost:4200"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
    }
}
