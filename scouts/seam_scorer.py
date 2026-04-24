"""
Scores a single seam for extraction risk using Claude.

Each seam is scored on four dimensions (1–10, higher = harder to extract):
  coupling          – how many other layers reference this seam
  test_coverage     – inverse: low tests = high risk
  data_tangle       – how entangled its data model is with other seams
  business_criticality – how much breakage would hurt in production

Returns a dict with the four scores, a weighted risk_score (1–10), and a rationale.
"""

import json
import anthropic

SYSTEM_PROMPT = """You are a senior software architect reviewing a Java Spring Boot application
called spring-music. Your job is to assess how risky it would be to extract a given code seam
into a standalone microservice or module.

For each seam you receive, return ONLY a JSON object with exactly this shape:
{
  "coupling": <int 1-10>,
  "test_coverage": <int 1-10>,
  "data_tangle": <int 1-10>,
  "business_criticality": <int 1-10>,
  "rationale": "<two sentences max>"
}

Scoring guide (higher number = higher extraction risk):
- coupling: 1 = self-contained, 10 = referenced everywhere
- test_coverage: 1 = well-tested, 10 = no tests at all (untested = risky to move)
- data_tangle: 1 = owns its data cleanly, 10 = shared tables / mixed persistence
- business_criticality: 1 = demo/seed data, 10 = core domain that must never break

Return only the JSON, no markdown fences, no extra keys.
"""

CODEBASE_CONTEXT = """
spring-music is a sample Spring Boot app demonstrating multi-backend persistence.
It stores a catalog of music albums and can persist them to H2, MySQL, PostgreSQL,
MongoDB, or Redis depending on the active Spring profile.

Key facts:
- Single AlbumRepository interface; four implementations selected via Spring profiles
- Domain model (Album) carries JPA annotations but is reused for Mongo too
- No dedicated service layer — controllers call repositories directly
- Very few unit tests; mostly integration/smoke tests
- Configuration reads Cloud Foundry VCAP_SERVICES to auto-configure the datasource
- AlbumRepositoryPopulator seeds demo data on every startup
"""


def score_seam(seam: dict, client: anthropic.Anthropic) -> dict:
    prompt = f"""{CODEBASE_CONTEXT}

Seam to assess:
  ID: {seam['id']}
  Name: {seam['name']}
  Description: {seam['description']}
  Files: {', '.join(seam['files'])}
  Notes: {seam['notes']}

Score this seam's extraction risk."""

    message = client.messages.create(
        model="claude-haiku-4-5-20251001",
        max_tokens=512,
        system=SYSTEM_PROMPT,
        messages=[{"role": "user", "content": prompt}],
    )

    raw = message.content[0].text.strip()
    scores = json.loads(raw)

    # Weighted risk score: coupling and business_criticality count most
    risk_score = round(
        scores["coupling"] * 0.30
        + scores["test_coverage"] * 0.20
        + scores["data_tangle"] * 0.25
        + scores["business_criticality"] * 0.25,
        1,
    )

    return {
        "id": seam["id"],
        "name": seam["name"],
        "coupling": scores["coupling"],
        "test_coverage": scores["test_coverage"],
        "data_tangle": scores["data_tangle"],
        "business_criticality": scores["business_criticality"],
        "risk_score": risk_score,
        "rationale": scores["rationale"],
    }
