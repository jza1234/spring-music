package org.cloudfoundry.samples.music.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.cloudfoundry.samples.music.domain.Album;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.repository.CrudRepository;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.NestedServletException;

import java.util.Arrays;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Characterization tests for AlbumController.
 *
 * Purpose: pin the CURRENT behaviour of the monolith before any modernisation
 * work begins — including bugs and quirks. If a test fails after a code change
 * it means behaviour has changed, intentionally or not.
 *
 * NOTE ON STARTUP DATA: on a fresh application start AlbumRepositoryPopulator
 * loads 28 albums from albums.json. These tests operate on a known set of 3
 * albums seeded in @Before to ensure test isolation.
 *
 * Run with: ./gradlew test --tests "*.AlbumControllerCharacterizationTest"
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
public class AlbumControllerCharacterizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CrudRepository<Album, String> albumRepository;

    // Known seed albums used across tests
    private Album beatles;
    private Album pinkFloyd;
    private Album milesDavis;

    @Before
    public void seedKnownAlbums() {
        albumRepository.deleteAll();
        beatles   = albumRepository.save(new Album("Abbey Road",                   "The Beatles",  "1969", "Rock"));
        pinkFloyd = albumRepository.save(new Album("The Dark Side of the Moon",    "Pink Floyd",   "1973", "Rock"));
        milesDavis = albumRepository.save(new Album("Kind of Blue",               "Miles Davis",  "1959", "Jazz"));
    }

    @After
    public void cleanUp() {
        albumRepository.deleteAll();
    }

    // -------------------------------------------------------------------------
    // GET /albums
    // -------------------------------------------------------------------------

    @Test
    public void getAllAlbums_returns200() throws Exception {
        mockMvc.perform(get("/albums"))
                .andExpect(status().isOk());
    }

    @Test
    public void getAllAlbums_returnsJson() throws Exception {
        mockMvc.perform(get("/albums"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    public void getAllAlbums_returnsAllSeededAlbums() throws Exception {
        mockMvc.perform(get("/albums"))
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    public void getAllAlbums_eachAlbumHasIdTitleArtistFields() throws Exception {
        mockMvc.perform(get("/albums"))
                .andExpect(jsonPath("$[0].id",     notNullValue()))
                .andExpect(jsonPath("$[0].title",  notNullValue()))
                .andExpect(jsonPath("$[0].artist", notNullValue()));
    }

    @Test
    public void getAllAlbums_albumShapeExposesAlbumIdField() throws Exception {
        // albumId is an internal field that leaks into the public API shape — pinning its presence
        mockMvc.perform(get("/albums"))
                .andExpect(jsonPath("$[0]").value(hasKey("albumId")));
    }

    @Test
    public void getAllAlbums_containsKnownSeededArtist() throws Exception {
        mockMvc.perform(get("/albums"))
                .andExpect(jsonPath("$[?(@.artist == 'The Beatles')]", hasSize(1)));
    }

    // -------------------------------------------------------------------------
    // PUT /albums — create
    // -------------------------------------------------------------------------

    @Test
    public void createAlbum_returns200() throws Exception {
        Album album = new Album("New Album", "New Artist", "2000", "Rock");

        mockMvc.perform(put("/albums")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(album)))
                .andExpect(status().isOk());
    }

    @Test
    public void createAlbum_responseContainsGeneratedId() throws Exception {
        Album album = new Album("New Album", "New Artist", "2000", "Rock");

        mockMvc.perform(put("/albums")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(album)))
                .andExpect(jsonPath("$.id", notNullValue()));
    }

    @Test
    public void createAlbum_generatedIdIsUuidFormat() throws Exception {
        Album album = new Album("UUID Test", "Artist", "2000", "Pop");

        mockMvc.perform(put("/albums")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(album)))
                .andExpect(jsonPath("$.id",
                        matchesRegex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")));
    }

    @Test
    public void createAlbum_echoesBackSubmittedFields() throws Exception {
        Album album = new Album("Echoed Title", "Echoed Artist", "1991", "Jazz");

        mockMvc.perform(put("/albums")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(album)))
                .andExpect(jsonPath("$.title",       is("Echoed Title")))
                .andExpect(jsonPath("$.artist",      is("Echoed Artist")))
                .andExpect(jsonPath("$.releaseYear", is("1991")))
                .andExpect(jsonPath("$.genre",       is("Jazz")));
    }

    @Test
    public void createAlbum_increasesTotalCountBy1() throws Exception {
        Album album = new Album("Extra Album", "Extra Artist", "2000", "Blues");

        mockMvc.perform(put("/albums")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(album)));

        mockMvc.perform(get("/albums"))
                .andExpect(jsonPath("$", hasSize(4)));
    }

    // -------------------------------------------------------------------------
    // POST /albums — update
    // -------------------------------------------------------------------------

    @Test
    public void updateAlbum_returns200() throws Exception {
        Album updated = new Album("Updated Title", "Updated Artist", "2001", "Classical");
        updated.setId(beatles.getId());

        mockMvc.perform(post("/albums")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk());
    }

    @Test
    public void updateAlbum_preservesIdAndChangesFields() throws Exception {
        Album updated = new Album("Changed Title", "Changed Artist", "2001", "Folk");
        updated.setId(beatles.getId());

        mockMvc.perform(post("/albums")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updated)))
                .andExpect(jsonPath("$.id",    is(beatles.getId())))
                .andExpect(jsonPath("$.title", is("Changed Title")));
    }

    @Test
    public void updateAlbum_doesNotChangeCount() throws Exception {
        Album updated = new Album("Same Count", "Artist", "2001", "Rock");
        updated.setId(beatles.getId());

        mockMvc.perform(post("/albums")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updated)));

        mockMvc.perform(get("/albums"))
                .andExpect(jsonPath("$", hasSize(3)));
    }

    // -------------------------------------------------------------------------
    // GET /albums/{id}
    // -------------------------------------------------------------------------

    @Test
    public void getById_returns200ForExistingId() throws Exception {
        mockMvc.perform(get("/albums/{id}", beatles.getId()))
                .andExpect(status().isOk());
    }

    @Test
    public void getById_returnsCorrectAlbumForExistingId() throws Exception {
        mockMvc.perform(get("/albums/{id}", beatles.getId()))
                .andExpect(jsonPath("$.id",    is(beatles.getId())))
                .andExpect(jsonPath("$.title", is("Abbey Road")));
    }

    /**
     * QUIRK: non-existent ID returns HTTP 200 with empty body — NOT 404.
     * Root cause: AlbumController.getById() calls repository.findById(id).orElse(null)
     * and returns null, which Spring serialises as an empty response body.
     */
    @Test
    public void getById_returns200WithEmptyBodyForNonExistentId() throws Exception {
        mockMvc.perform(get("/albums/{id}", "id-that-does-not-exist"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    // -------------------------------------------------------------------------
    // DELETE /albums/{id}
    // -------------------------------------------------------------------------

    @Test
    public void deleteById_returns200ForExistingId() throws Exception {
        mockMvc.perform(delete("/albums/{id}", beatles.getId()))
                .andExpect(status().isOk());
    }

    @Test
    public void deleteById_returnsEmptyBodyForExistingId() throws Exception {
        mockMvc.perform(delete("/albums/{id}", beatles.getId()))
                .andExpect(content().string(""));
    }

    @Test
    public void deleteById_removesAlbumFromGetAll() throws Exception {
        mockMvc.perform(delete("/albums/{id}", beatles.getId()));

        mockMvc.perform(get("/albums"))
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    public void deleteById_afterDeletionGetByIdReturnsEmptyBody() throws Exception {
        mockMvc.perform(delete("/albums/{id}", beatles.getId()));

        mockMvc.perform(get("/albums/{id}", beatles.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    /**
     * QUIRK: DELETE on a non-existent ID throws EmptyResultDataAccessException
     * (HTTP 500), instead of silently returning 200 or 404.
     * Root cause: Spring Data JPA's deleteById() throws when entity not found.
     * No global exception handler exists in the monolith to soften this.
     */
    @Test
    public void deleteById_throws500ForNonExistentId() throws Exception {
        try {
            mockMvc.perform(delete("/albums/{id}", "id-that-does-not-exist"));
            fail("Expected NestedServletException wrapping EmptyResultDataAccessException");
        } catch (NestedServletException e) {
            assertThat(e.getCause().getClass().getSimpleName(),
                    is("EmptyResultDataAccessException"));
        }
    }
}
