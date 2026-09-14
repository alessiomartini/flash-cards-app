#!/usr/bin/env python3
"""Exports vocabulary from the online D1 database into an Anki-importable text file.

Usage:
    CF_ACCOUNT_ID=... CF_D1_DATABASE_ID=... CF_API_TOKEN=... \
        python3 scripts/export_to_anki.py en engvocab-anki-import.txt

Reads the same three Cloudflare credentials as the :cli import tool (see "Importing your
vocabulary" in the README) - an API token with D1:Edit is enough since this only reads.

Produces a tab-separated file using Anki's plain-text import format (the "#..." header lines
are read by Anki itself - see https://docs.ankiweb.net/importing/text-files.html): each row
maps to a "Basic (and reversed card)" note (a built-in Anki note type), so importing it creates
both directions - front->back and back->front - from every word, same as EngVocab's own
term<->meaning study modes. Doesn't (and can't) carry over FSRS review history: Anki has its
own scheduler and no way to import stability/difficulty from a text file, so every card starts
"new" in Anki. It also only covers what's in D1 - cards added manually on a phone and never
synced up aren't included (see "Cloud sync is one-directional" in the README).
"""

import html
import json
import os
import re
import sys
import urllib.request

D1_QUERY = """
    SELECT front, back, definition, example, part_of_speech, tags, source
    FROM words
    WHERE is_deleted = 0 AND language = ?
    ORDER BY front COLLATE NOCASE
"""


def fetch_words(account_id: str, database_id: str, api_token: str, language: str) -> list[dict]:
    url = f"https://api.cloudflare.com/client/v4/accounts/{account_id}/d1/database/{database_id}/query"
    body = json.dumps({"sql": D1_QUERY, "params": [language]}).encode("utf-8")
    request = urllib.request.Request(
        url,
        data=body,
        headers={"Authorization": f"Bearer {api_token}", "Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(request) as response:
        envelope = json.load(response)
    if not envelope.get("success"):
        errors = "; ".join(e.get("message", "") for e in envelope.get("errors", []))
        raise SystemExit(f"D1 query failed: {errors or 'unknown error'}")
    return envelope["result"][0]["results"]


def clean_field(value: str | None) -> str:
    if not value:
        return ""
    value = str(value).strip().replace("\t", " ")
    value = value.replace("\r\n", "<br>").replace("\n", "<br>")
    return html.escape(value, quote=False).replace("&lt;br&gt;", "<br>")


def tag_token(value: str) -> str:
    return re.sub(r"\s+", "_", value.strip())


def build_line(row: dict) -> str | None:
    front = clean_field(row.get("front"))
    back = clean_field(row.get("back"))
    if not front or not back:
        return None

    definition = clean_field(row.get("definition"))
    example = clean_field(row.get("example"))
    part_of_speech = clean_field(row.get("part_of_speech"))

    back_html = back
    if definition:
        back_html += f'<br><span style="color:#888888">{definition}</span>'
    if example:
        back_html += f"<br><i>{example}</i>"

    tags = []
    source = (row.get("source") or "").strip().lower()
    if source:
        tags.append(tag_token(source))
    if part_of_speech:
        tags.append(tag_token(part_of_speech.lower()))
    raw_tags = (row.get("tags") or "").strip()
    if raw_tags:
        tags.extend(tag_token(t) for t in raw_tags.split(",") if t.strip())

    return "\t".join([front, back_html, " ".join(tags)])


def main() -> None:
    if len(sys.argv) < 2:
        raise SystemExit(f"usage: {sys.argv[0]} <language-code> [output-file]")
    language = sys.argv[1]
    out_path = sys.argv[2] if len(sys.argv) > 2 else f"engvocab-{language}-anki-import.txt"

    account_id = os.environ.get("CF_ACCOUNT_ID")
    database_id = os.environ.get("CF_D1_DATABASE_ID")
    api_token = os.environ.get("CF_API_TOKEN")
    if not all([account_id, database_id, api_token]):
        raise SystemExit("Set CF_ACCOUNT_ID, CF_D1_DATABASE_ID, and CF_API_TOKEN first.")

    rows = fetch_words(account_id, database_id, api_token, language)
    lines = [line for row in rows if (line := build_line(row)) is not None]

    header = [
        "#separator:tab",
        "#html:true",
        "#notetype:Basic (and reversed card)",
        f"#deck:EngVocab::{language}",
        "#tags column:3",
    ]
    with open(out_path, "w", encoding="utf-8") as f:
        f.write("\n".join(header) + "\n")
        f.write("\n".join(lines) + "\n")

    skipped = len(rows) - len(lines)
    print(f"Wrote {len(lines)} cards to {out_path}" + (f" (skipped {skipped} with no translation)" if skipped else ""))


if __name__ == "__main__":
    main()
