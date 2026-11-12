"""Generate Egyptian Arabic instruction audio with ElevenLabs (Story 2.5).

For every step and activity ID in the catalogue, writes ``<id>.mp3`` (normal) and
``<id>.slow.mp3`` (lower speaking rate) to ``android/app/src/main/assets/audio/ar-eg/``.

Resumable: ``manifest.json`` stores a hash per file, so unchanged text makes no API call.
When the account's character quota runs out the run stops cleanly (exit 3) and a later run,
possibly with another account's key, continues where it stopped.

Exit codes: 0 ok, 1 missing strings, 2 missing API key, 3 quota exhausted, 4 other API error.
The API key is read only from the ``ELEVENLABS_API_KEY`` environment variable and is never
printed, logged or written.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any

TOOL_DIR = Path(__file__).resolve().parent
REPO_ROOT = TOOL_DIR.parents[1]
sys.path.insert(0, str(REPO_ROOT / "tools" / "catalog-builder"))

import build_catalog  # noqa: E402

STRINGS_FILE = REPO_ROOT / "tools" / "catalog-builder" / "strings.ar-eg.json"
CONFIG_FILE = TOOL_DIR / "config.json"
MANIFEST_FILE = TOOL_DIR / "manifest.json"
REVIEW_FILE = TOOL_DIR / "review.csv"
OUTPUT_DIR = REPO_ROOT / "android" / "app" / "src" / "main" / "assets" / "audio" / "ar-eg"
KEY_ENV = "ELEVENLABS_API_KEY"

EXIT_MISSING_STRINGS = 1
EXIT_MISSING_KEY = 2
EXIT_QUOTA = 3
EXIT_API_ERROR = 4

REVIEW_FIELDS = ("file", "id", "variant", "status")
STATUS_PENDING = "pending"
STATUS_REGENERATE = "regenerate"


class QuotaExhausted(Exception):
    """The ElevenLabs account has no characters left (or is rate limited)."""


@dataclass(frozen=True)
class Job:
    id: str
    variant: str
    text: str
    file_name: str
    settings: dict[str, Any]
    hash: str


def load_json(path: Path, default: Any = None) -> Any:
    if not path.exists():
        return default
    return json.loads(path.read_text(encoding="utf-8"))


def catalogue_ids() -> tuple[list[str], list[str]]:
    steps = [s.id for s in build_catalog.parse_steps()]
    activities = [a.id for a in build_catalog.parse_activities()]
    return steps, activities


def collect_texts(strings: dict, step_ids: list[str], activity_ids: list[str]) -> tuple[dict, list[str]]:
    """Return ``{id: text}`` in catalogue order and the IDs that have no sentence."""
    texts: dict[str, str] = {}
    missing: list[str] = []
    for section, ids in (("steps", step_ids), ("activities", activity_ids)):
        table = strings.get(section, {})
        for item_id in ids:
            text = (table.get(item_id) or "").strip()
            if text:
                texts[item_id] = text
            else:
                missing.append(item_id)
    return texts, missing


def text_hash(text: str, config: dict, settings: dict) -> str:
    payload = json.dumps(
        {
            "text": text,
            "voiceId": config["voiceId"],
            "modelId": config["modelId"],
            "outputFormat": config["outputFormat"],
            "voiceSettings": settings,
        },
        sort_keys=True,
        ensure_ascii=False,
    )
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def build_jobs(texts: dict[str, str], config: dict) -> list[Job]:
    jobs = []
    for item_id, text in texts.items():
        for variant, spec in config["variants"].items():
            settings = spec["voiceSettings"]
            jobs.append(
                Job(
                    id=item_id,
                    variant=variant,
                    text=text,
                    file_name=f"{item_id}{spec['suffix']}",
                    settings=settings,
                    hash=text_hash(text, config, settings),
                )
            )
    return jobs


def read_review(path: Path) -> dict[str, dict[str, str]]:
    if not path.exists():
        return {}
    with path.open(encoding="utf-8", newline="") as f:
        return {row["file"]: row for row in csv.DictReader(f)}


def write_review(path: Path, jobs: list[Job], review: dict[str, dict[str, str]]) -> None:
    rows = []
    for job in jobs:
        status = review.get(job.file_name, {}).get("status") or STATUS_PENDING
        rows.append({"file": job.file_name, "id": job.id, "variant": job.variant, "status": status})
    tmp = path.with_suffix(".csv.tmp")
    with tmp.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=REVIEW_FIELDS)
        writer.writeheader()
        writer.writerows(rows)
    os.replace(tmp, path)


def write_manifest(path: Path, manifest: dict) -> None:
    tmp = path.with_suffix(".json.tmp")
    text = json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    tmp.write_text(text, encoding="utf-8")
    os.replace(tmp, path)


def needs_call(job: Job, manifest: dict, review: dict, output_dir: Path) -> bool:
    if review.get(job.file_name, {}).get("status") == STATUS_REGENERATE:
        return True
    entry = manifest.get("files", {}).get(job.file_name)
    return not (entry and entry.get("hash") == job.hash and (output_dir / job.file_name).exists())


def is_quota_error(exc: Exception) -> bool:
    status = getattr(exc, "status_code", None)
    if status == 429:
        return True
    if status == 401:
        return "quota_exceeded" in json.dumps(getattr(exc, "body", None), default=str)
    return False


def api_error_message(exc: Exception) -> str:
    body = getattr(exc, "body", None)
    detail = body.get("detail") if isinstance(body, dict) else body
    if isinstance(detail, dict):
        detail = detail.get("message") or detail.get("status") or detail.get("code")
    return " ".join(str(detail or "no details").split())


def make_client() -> Any:
    from elevenlabs import ElevenLabs

    return ElevenLabs(api_key=os.environ[KEY_ENV])


def synthesize(client: Any, job: Job, config: dict) -> bytes:
    from elevenlabs import VoiceSettings

    kwargs: dict[str, Any] = {
        "text": job.text,
        "model_id": config["modelId"],
        "output_format": config["outputFormat"],
        "voice_settings": VoiceSettings(**job.settings),
    }
    if config.get("languageCode"):
        kwargs["language_code"] = config["languageCode"]
    audio = client.text_to_speech.convert(config["voiceId"], **kwargs)
    return audio if isinstance(audio, bytes) else b"".join(audio)


def generate(
    jobs: list[Job], config: dict, client: Any, manifest: dict, review: dict, output_dir: Path
) -> int:
    """Run the API calls. Returns the number of calls made; raises QuotaExhausted."""
    output_dir.mkdir(parents=True, exist_ok=True)
    files = manifest.setdefault("files", {})
    calls = 0
    for job in jobs:
        if not needs_call(job, manifest, review, output_dir):
            continue
        try:
            data = synthesize(client, job, config)
        except Exception as exc:
            if is_quota_error(exc):
                raise QuotaExhausted() from None
            raise
        calls += 1
        target = output_dir / job.file_name
        tmp = target.with_name(target.name + ".part")
        tmp.write_bytes(data)
        os.replace(tmp, target)
        files[job.file_name] = {
            "id": job.id,
            "variant": job.variant,
            "hash": job.hash,
            "voiceId": config["voiceId"],
            "modelId": config["modelId"],
            "outputFormat": config["outputFormat"],
            "voiceSettings": job.settings,
        }
        review[job.file_name] = {"status": STATUS_PENDING}
    return calls


def main(
    argv: list[str] | None = None,
    *,
    client: Any = None,
    strings_file: Path = STRINGS_FILE,
    config_file: Path = CONFIG_FILE,
    manifest_file: Path = MANIFEST_FILE,
    review_file: Path = REVIEW_FILE,
    output_dir: Path = OUTPUT_DIR,
    ids: tuple[list[str], list[str]] | None = None,
) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--dry-run", action="store_true", help="print character totals, no API calls")
    parser.add_argument("--only", metavar="ID", help="generate only this step or activity ID")
    args = parser.parse_args(argv)

    config = load_json(config_file)
    strings = load_json(strings_file, {})
    step_ids, activity_ids = ids if ids is not None else catalogue_ids()
    texts, missing = collect_texts(strings, step_ids, activity_ids)
    if missing:
        print(f"Missing Arabic sentence for {len(missing)} ID(s):", file=sys.stderr)
        for item_id in missing:
            print(f"  {item_id}", file=sys.stderr)
        return EXIT_MISSING_STRINGS

    all_jobs = build_jobs(texts, config)
    jobs = all_jobs
    if args.only:
        if args.only not in texts:
            print(f"Unknown ID: {args.only}", file=sys.stderr)
            return EXIT_MISSING_STRINGS
        jobs = [j for j in all_jobs if j.id == args.only]

    manifest = load_json(manifest_file, {}) or {}
    review = read_review(review_file)
    pending = [j for j in jobs if needs_call(j, manifest, review, output_dir)]

    if args.dry_run:
        for variant in config["variants"]:
            total = sum(len(j.text) for j in jobs if j.variant == variant)
            todo = sum(len(j.text) for j in pending if j.variant == variant)
            print(f"{variant}: {total} characters total, {todo} still to generate")
        print(
            f"all: {sum(len(j.text) for j in jobs)} characters total, "
            f"{sum(len(j.text) for j in pending)} still to generate ({len(pending)} files)"
        )
        return 0

    if client is None:
        if not os.environ.get(KEY_ENV):
            print(f"{KEY_ENV} is not set. Export it in your shell and run again.", file=sys.stderr)
            return EXIT_MISSING_KEY
        client = make_client()

    try:
        calls = generate(jobs, config, client, manifest, review, output_dir)
    except QuotaExhausted:
        write_manifest(manifest_file, manifest)
        write_review(review_file, all_jobs, review)
        remaining = [j for j in jobs if needs_call(j, manifest, review, output_dir)]
        chars = sum(len(j.text) for j in remaining)
        print(
            f"ElevenLabs quota exhausted. {len(remaining)} file(s) remain ({chars} characters). "
            "Run again later or with another account's key to continue.",
            file=sys.stderr,
        )
        return EXIT_QUOTA
    except Exception as exc:
        status = getattr(exc, "status_code", None)
        if status is None:
            write_manifest(manifest_file, manifest)
            write_review(review_file, all_jobs, review)
            raise
        write_manifest(manifest_file, manifest)
        write_review(review_file, all_jobs, review)
        print(f"ElevenLabs API error {status}: {api_error_message(exc)}", file=sys.stderr)
        return EXIT_API_ERROR
    write_manifest(manifest_file, manifest)
    write_review(review_file, all_jobs, review)
    print(f"Generated {calls} file(s); {len(jobs) - calls} unchanged.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
