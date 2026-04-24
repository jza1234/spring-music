package org.cloudfoundry.samples.music.web;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.NestedServletException;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Characterization tests for InfoController and ErrorController.
 * Pins the current behaviour of /appinfo, /service, and /errors/* endpoints.
 *
 * Run with: ./gradlew test --tests "*.InfoAndErrorControllerCharacterizationTest"
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
public class InfoAndErrorControllerCharacterizationTest {

    @Autowired
    private MockMvc mockMvc;

    // -------------------------------------------------------------------------
    // GET /appinfo
    // -------------------------------------------------------------------------

    @Test
    public void appinfo_returns200() throws Exception {
        mockMvc.perform(get("/appinfo"))
                .andExpect(status().isOk());
    }

    @Test
    public void appinfo_returnsJson() throws Exception {
        mockMvc.perform(get("/appinfo"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    public void appinfo_responseShapeHasProfilesAndServicesArrays() throws Exception {
        mockMvc.perform(get("/appinfo"))
                .andExpect(jsonPath("$.profiles").isArray())
                .andExpect(jsonPath("$.services").isArray());
    }

    @Test
    public void appinfo_localRunHasNoActiveProfilesAndNoServices() throws Exception {
        // Running locally without Cloud Foundry: no profiles activated,
        // no services bound — H2 is the silent default fallback.
        mockMvc.perform(get("/appinfo"))
                .andExpect(jsonPath("$.profiles", hasSize(0)))
                .andExpect(jsonPath("$.services", hasSize(0)));
    }

    // -------------------------------------------------------------------------
    // GET /service
    // -------------------------------------------------------------------------

    @Test
    public void service_returns200() throws Exception {
        mockMvc.perform(get("/service"))
                .andExpect(status().isOk());
    }

    @Test
    public void service_returnsEmptyArrayLocally() throws Exception {
        mockMvc.perform(get("/service"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // -------------------------------------------------------------------------
    // GET /errors/throw
    // -------------------------------------------------------------------------

    /**
     * QUIRK: /errors/throw is an intentional chaos endpoint that throws
     * NullPointerException. In the live app this produces HTTP 500.
     * In MockMvc the exception propagates as NestedServletException because
     * the monolith has no global exception handler — the Servlet container
     * error dispatch mechanism does the 500 mapping in production.
     */
    @Test
    public void errorsThrow_throwsNullPointerExceptionTowardsCaller() throws Exception {
        try {
            mockMvc.perform(get("/errors/throw"));
            fail("Expected NestedServletException wrapping NullPointerException");
        } catch (NestedServletException e) {
            assertThat(e.getCause(), instanceOf(NullPointerException.class));
        }
    }
}
