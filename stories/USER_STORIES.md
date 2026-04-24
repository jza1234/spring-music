# User Stories — Spring Music / Northwind Logistics Catalog

> **Context:** Northwind Logistics runs a catalog service built on Spring Boot 2.4. The board
> approved "modernization" without defining it. These stories capture what the system must continue
> to do correctly through any modernization effort, and where stakeholders disagree about scope.
>
> Stories are written against the current API surface so they can be used directly as a basis
> for characterization tests (The Pin) before any refactoring begins.

---

## Personas

| Persona | Who they are |
|---|---|
| **Catalog User** | Internal staff browsing the album/catalog data |
| **Catalog Curator** | Staff who add, edit, or remove catalog entries |
| **Ops Engineer** | Responsible for deployment, monitoring, and uptime |
| **Developer** | Engineer working on the codebase |

---

## CAP-1: Browse the Album Catalog

### CAP-1-S1: View the full catalog

**As a** Catalog User  
**I want** to retrieve a list of all albums in the system  
**So that** I can see everything currently in the catalog

**Acceptance Criteria:**
1. `GET /albums` returns HTTP 200
2. The response body is a JSON array
3. Each element contains at minimum: `id`, `title`, `artist`, `releaseYear`, `genre`, `trackCount`
4. When the catalog has been pre-populated (first startup), the response contains at least 20 albums
5. An empty catalog returns an empty JSON array `[]`, not a 404 or error

**Out of scope:** Pagination, filtering, sorting

---

### CAP-1-S2: Retrieve a single album by ID

**As a** Catalog User  
**I want** to fetch one album by its unique identifier  
**So that** I can view its full details without loading the entire catalog

**Acceptance Criteria:**
1. `GET /albums/{id}` with a valid existing ID returns HTTP 200 and the album object
2. The response contains all fields: `id`, `title`, `artist`, `releaseYear`, `genre`, `trackCount`, `albumId`
3. `GET /albums/{id}` with a non-existent ID returns HTTP 404
4. `GET /albums/{id}` with a malformed or empty ID returns HTTP 400 or 404 (not 500)

**Out of scope:** Fuzzy lookup by title or artist

---

## CAP-2: Add / Update / Remove Albums

### CAP-2-S1: Add a new album

**As a** Catalog Curator  
**I want** to add a new album to the catalog  
**So that** the collection stays current

**Acceptance Criteria:**
1. `PUT /albums` with a valid JSON body (title, artist, releaseYear, genre, trackCount) returns HTTP 200 or 201
2. The response body includes the newly created album with a system-generated `id` (UUID format)
3. The new album is retrievable via `GET /albums/{id}` immediately after creation
4. `PUT /albums` with a missing required field (e.g. no `title`) returns HTTP 400, not 500
5. Two successive `PUT /albums` calls with identical data create two distinct albums with different IDs

**Out of scope:** Duplicate detection, image upload

> **Stakeholder disagreement — Priority:**  
> *Dev team* wants this story deprioritized until the multi-DB profile layer (CAP-3) is stabilized first.  
> *Business* wants CAP-2 as the first extraction target because curators are blocked by the current UI.  
> **Current status: unresolved.** The decomposition plan (The Map) must explicitly address this before sprint planning.

---

### CAP-2-S2: Update an existing album

**As a** Catalog Curator  
**I want** to correct or update metadata on an existing album  
**So that** the catalog reflects accurate information

**Acceptance Criteria:**
1. `POST /albums` with a valid JSON body containing an existing `id` returns HTTP 200
2. The updated fields are reflected in subsequent `GET /albums/{id}` responses
3. Fields not included in the request body are not cleared or nulled out
4. `POST /albums` with an `id` that does not exist returns HTTP 404, not a silent insert
5. Updating `id` itself is rejected (HTTP 400) — IDs are immutable

**Out of scope:** Partial patch (PATCH), field-level audit trail

> **Stakeholder disagreement — Test coverage gate:**  
> *QA* requires characterization tests (The Pin) to be green before any changes to the update path.  
> *PM* has delivery pressure to ship the curator UI fix this sprint.  
> **Current status: unresolved.** Recommend QA's gate wins — a broken update path in prod is worse than a delayed sprint.

---

### CAP-2-S3: Delete an album

**As a** Catalog Curator  
**I want** to remove an album from the catalog  
**So that** the collection only contains relevant entries

**Acceptance Criteria:**
1. `DELETE /albums/{id}` with a valid existing ID returns HTTP 200 or 204
2. Subsequent `GET /albums/{id}` for the deleted album returns HTTP 404
3. The deleted album no longer appears in `GET /albums`
4. `DELETE /albums/{id}` with a non-existent ID returns HTTP 404, not 200
5. `DELETE /albums/{id}` is idempotent only in the sense that a second call returns 404, not 200

**Out of scope:** Soft delete, archive, undo

---

## CAP-3: Database Backend Flexibility

### CAP-3-S1: Application starts and serves data against any supported backend

**As an** Ops Engineer  
**I want** the application to function correctly regardless of which database backend is bound  
**So that** I can deploy to any environment without code changes

**Acceptance Criteria:**
1. With no active profile (default), the app starts using the embedded H2 in-memory database and `GET /albums` returns data
2. With profile `mysql`, the app connects to MySQL and `GET /albums` returns data (requires a running MySQL instance)
3. With profile `mongodb`, the app connects to MongoDB and `GET /albums` returns data
4. With profile `redis`, the app connects to Redis and `GET /albums` returns data
5. Activating more than one database profile simultaneously causes the application to fail at startup with a clear error message (not a silent data inconsistency)
6. Switching profiles does not change the API response structure — `GET /albums` returns the same JSON shape regardless of backend

**Out of scope:** Live migration of data between backends, simultaneous multi-backend writes

> **Stakeholder disagreement — "Modernize" means what?**  
> *CTO* interprets modernization as decomposing this multi-profile capability into separate microservices, one per database type, behind an API gateway.  
> *Ops* says: containerize the monolith as-is and ship it; profile switching is a feature, not a problem.  
> **Current status: unresolved.** This is the highest-impact disagreement in the project. The ADR (The Map) must make a binding decision before CAP-3 is touched.

---

## CAP-4: Sample Data Initialization

### CAP-4-S1: Catalog is pre-populated on first startup

**As a** Catalog User  
**I want** the catalog to contain sample data when first deployed  
**So that** the system is immediately usable for demonstration and onboarding

**Acceptance Criteria:**
1. On first startup against an empty database, `GET /albums` returns exactly 28 albums
2. The sample albums include entries from the following artists: The Beatles, The Rolling Stones, Led Zeppelin, Nirvana (spot-check)
3. On subsequent restarts against a non-empty database, no duplicate albums are added
4. If a curator has deleted albums, a restart does not restore the deleted items
5. The initialization completes before the first HTTP request is served (no race condition window where `GET /albums` returns `[]` briefly)

**Out of scope:** Seeding with production data, environment-specific seed sets

> **Stakeholder disagreement — Sample data in production:**  
> *Business* wants the sample data removed from production deployments — classic rock albums in a logistics catalog looks unprofessional.  
> *Dev* wants to keep it — it's the only reliable way to demo the system end-to-end.  
> **Recommended resolution:** Make the seed data opt-in via a feature flag or Spring profile (e.g. `--spring.profiles.active=seed`). Neither side loses. This should be a story in the next sprint.

---

## CAP-5: Application Health Visibility

### CAP-5-S1: Ops can see which database profile is active at runtime

**As an** Ops Engineer  
**I want** to query the running application and know which database backend and Spring profiles are active  
**So that** I can confirm the correct configuration was applied after deployment

**Acceptance Criteria:**
1. `GET /appinfo` returns HTTP 200
2. The response includes a `profiles` field listing all active Spring profiles as an array
3. When running with the H2 default, `profiles` contains an entry reflecting that (empty array or `["default"]` is acceptable)
4. When running with `mysql` profile, `profiles` contains `"mysql"`
5. The response includes a `services` field — it may be empty in local/non-CF deployments but must be present
6. `GET /appinfo` is available without authentication

**Out of scope:** Prometheus metrics, distributed tracing, log aggregation

---

## Priority Stack Rank (PM recommendation)

| Rank | Story | Rationale |
|---|---|---|
| 1 | CAP-1-S1, CAP-1-S2 | Read path must be stable before any extraction |
| 2 | CAP-5-S1 | Ops needs visibility before any deployment changes |
| 3 | CAP-4-S1 | Hidden dependency — must be pinned before refactoring |
| 4 | CAP-2-S1, CAP-2-S2, CAP-2-S3 | Write path — high business value but blocked by test coverage gate |
| 5 | CAP-3-S1 | Most complex, most disputed — needs ADR before implementation |

> **Note:** This rank is the PM's recommendation. Dev team and Ops have both objected to items 4 and 5 respectively. See stakeholder notes in each story. The decomposition plan (The Map) is a prerequisite for resolving CAP-3.
