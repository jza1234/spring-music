"""
Fan-out coordinator: scores all seams in parallel, then writes a ranked report.

Usage:
    python scouts/coordinator.py

Requires ANTHROPIC_API_KEY in the environment.
Output: scouts/results/ranked_seams.json
"""

import asyncio
import json
import os
import sys
from datetime import datetime
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor

import anthropic
from seam_scorer import score_seam

SEAMS_FILE = Path(__file__).parent / "seams.json"
RESULTS_FILE = Path(__file__).parent / "results" / "ranked_seams.json"


def score_seam_sync(args):
    """Thin wrapper so asyncio can run score_seam in a thread pool."""
    seam, client = args
    print(f"  [scout] scoring {seam['id']} — {seam['name']} ...", flush=True)
    result = score_seam(seam, client)
    print(f"  [scout] done    {seam['id']} — risk_score={result['risk_score']}", flush=True)
    return result


async def fan_out(seams: list, client: anthropic.Anthropic) -> list:
    """Launch one scoring task per seam, all in parallel via a thread pool."""
    loop = asyncio.get_event_loop()
    with ThreadPoolExecutor(max_workers=len(seams)) as pool:
        tasks = [
            loop.run_in_executor(pool, score_seam_sync, (seam, client))
            for seam in seams
        ]
        results = await asyncio.gather(*tasks)
    return list(results)


def build_report(scored: list, project: str) -> dict:
    ranked = sorted(scored, key=lambda x: x["risk_score"], reverse=True)

    # Simple recommendation: lowest risk = extract first
    for i, seam in enumerate(ranked):
        if seam["risk_score"] <= 3.5:
            seam["recommendation"] = "Extract first — low coupling and risk"
        elif seam["risk_score"] <= 6.0:
            seam["recommendation"] = "Extract after low-risk seams are stable"
        else:
            seam["recommendation"] = "Extract last — high risk, needs refactor first"

    return {
        "project": project,
        "generated_at": datetime.utcnow().isoformat() + "Z",
        "model": "claude-haiku-4-5-20251001",
        "scoring_dimensions": {
            "coupling": "weight 30% — cross-layer references",
            "test_coverage": "weight 20% — inverse: no tests = high risk",
            "data_tangle": "weight 25% — shared / entangled data model",
            "business_criticality": "weight 25% — production impact if broken",
        },
        "ranked_seams": ranked,
    }


async def main():
    api_key = os.environ.get("ANTHROPIC_API_KEY")
    if not api_key:
        print("ERROR: ANTHROPIC_API_KEY not set", file=sys.stderr)
        sys.exit(1)

    with open(SEAMS_FILE) as f:
        manifest = json.load(f)

    seams = manifest["seams"]
    project = manifest["project"]

    print(f"\nspring-music scout coordinator")
    print(f"  project : {project}")
    print(f"  seams   : {len(seams)}")
    print(f"  mode    : parallel fan-out ({len(seams)} subagents)\n")

    client = anthropic.Anthropic(api_key=api_key)

    scored = await fan_out(seams, client)

    report = build_report(scored, project)

    RESULTS_FILE.parent.mkdir(parents=True, exist_ok=True)
    with open(RESULTS_FILE, "w") as f:
        json.dump(report, f, indent=2)

    print(f"\n--- RANKED EXTRACTION ORDER ---")
    for seam in report["ranked_seams"]:
        print(
            f"  {seam['id']}  risk={seam['risk_score']:4.1f}  {seam['name']:<35}  {seam['recommendation']}"
        )

    print(f"\nFull report written to: {RESULTS_FILE}\n")


if __name__ == "__main__":
    asyncio.run(main())
