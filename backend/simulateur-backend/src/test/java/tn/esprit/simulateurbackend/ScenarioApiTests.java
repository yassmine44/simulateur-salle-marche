package tn.esprit.simulateurbackend;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tn.esprit.simulateurbackend.service.ScenarioService;
import java.util.List;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:scenario-api-tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@DirtiesContext
class ScenarioApiTests {
    @Autowired WebApplicationContext context;
    @Autowired ScenarioService scenarios;
    MockMvc mvc;
    private static final String BODY = "{\"type\":\"CRASH\",\"symbol\":\"AAPL\",\"durationSeconds\":60,\"intensity\":0.5}";
    @BeforeEach void setup() {
        scenarios.stop();
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }
    @AfterEach void cleanup() { scenarios.stop(); }
    private MockHttpSession session(String role) {
        var session = new MockHttpSession();
        var auth = new UsernamePasswordAuthenticationToken("scenario-test", "unused",
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, new SecurityContextImpl(auth));
        return session;
    }
    @Test void userCannotReadOrStartScenarios() throws Exception {
        var session = session("USER");
        mvc.perform(get("/api/admin/scenarios/status").session(session)).andExpect(status().isForbidden());
        var token = mvc.perform(get("/api/csrf").session(session)).andReturn().getResponse().getCookie("XSRF-TOKEN");
        mvc.perform(post("/api/admin/scenarios/start").session(session).cookie(token)
                .header("X-XSRF-TOKEN", token.getValue()).contentType("application/json").content(BODY))
                .andExpect(status().isForbidden());
    }
    @Test void adminMutationsRequireCsrf() throws Exception {
        var session = session("ADMIN");
        mvc.perform(post("/api/admin/scenarios/start").session(session).contentType("application/json").content(BODY))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/scenarios/stop").session(session)).andExpect(status().isForbidden());
    }
    @Test void adminCanStartReadAndStopAndConflictingStartIsRejected() throws Exception {
        var session = session("ADMIN");
        var token = mvc.perform(get("/api/csrf").session(session)).andReturn().getResponse().getCookie("XSRF-TOKEN");
        mvc.perform(post("/api/admin/scenarios/start").session(session).cookie(token)
                .header("X-XSRF-TOKEN", token.getValue()).contentType("application/json").content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true)).andExpect(jsonPath("$.symbol").value("AAPL"));
        mvc.perform(get("/api/admin/scenarios/status").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("CRASH"));
        mvc.perform(post("/api/admin/scenarios/start").session(session).cookie(token)
                .header("X-XSRF-TOKEN", token.getValue()).contentType("application/json").content(BODY))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/admin/scenarios/stop").session(session).cookie(token)
                .header("X-XSRF-TOKEN", token.getValue())).andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }
    @Test void invalidParametersReturnBadRequest() throws Exception {
        var session = session("ADMIN");
        var token = mvc.perform(get("/api/csrf").session(session)).andReturn().getResponse().getCookie("XSRF-TOKEN");
        mvc.perform(post("/api/admin/scenarios/start").session(session).cookie(token)
                .header("X-XSRF-TOKEN", token.getValue()).contentType("application/json").content(BODY.replace("60", "10")))
                .andExpect(status().isBadRequest());
    }
    @Test void marketMetricsRequireAdmin() throws Exception {
        mvc.perform(get("/api/admin/scenarios/market/AAPL").session(session("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/scenarios/market/AAPL").session(session("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.capturedAt").exists());
    }
    @Test void unknownMarketIs404ForAdmin() throws Exception {
        mvc.perform(get("/api/admin/scenarios/market/UNKNOWN").session(session("ADMIN")))
                .andExpect(status().isNotFound());
    }}
