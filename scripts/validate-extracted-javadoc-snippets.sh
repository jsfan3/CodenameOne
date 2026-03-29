#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: scripts/validate-extracted-javadoc-snippets.sh <snippets.jsonl>" >&2
  exit 2
fi

JSONL_FILE="$1"
if [[ ! -f "$JSONL_FILE" ]]; then
  echo "JSONL file not found: $JSONL_FILE" >&2
  exit 2
fi

python3 - "$JSONL_FILE" <<'PY'
import json
import pathlib
import re
import subprocess
import sys
import tempfile

jsonl_path = pathlib.Path(sys.argv[1])
failures = []
excluded = []
count = 0

EXCLUSION_RULES = [
    (
        re.compile(r"Encountered: \"`\""),
        "Malformed markdown/code fence content in source docs (contains literal backticks).",
    ),
    (
        re.compile(r"Util\.getImplementation\(\).*is null|Display\.impl.*is null"),
        "Snippet requires a live Codename One runtime (not available in playground conversion harness).",
    ),
    (
        re.compile(r"Class: .* not found in namespace|undefined variable or class name|Undefined argument:"),
        "Snippet is partial/illustrative and omits surrounding declarations/imports.",
    ),
    (
        re.compile(r"Generated instance dispatch not implemented|Command not found:"),
        "Snippet depends on framework dispatch/features not supported by the conversion harness.",
    ),
    (
        re.compile(r"Parse error:|Lexical error at line"),
        "Snippet is not standalone Java for direct harness evaluation (requires adaptation).",
    ),
]


def exclusion_reason(result: str):
    for pattern, reason in EXCLUSION_RULES:
        if pattern.search(result):
            return reason
    return None


for line in jsonl_path.read_text(encoding="utf-8").splitlines():
    if not line.strip():
        continue
    record = json.loads(line)
    count += 1
    with tempfile.NamedTemporaryFile("w", suffix=".java", delete=False, encoding="utf-8") as tmp:
        tmp.write(record.get("code", ""))
        tmp_path = tmp.name
    proc = subprocess.run(
        ["scripts/java-snippet-to-playground-uri.sh", "--file", tmp_path],
        text=True,
        capture_output=True,
        check=False,
    )
    output = (proc.stdout or "").strip()
    if proc.returncode != 0 or not output.startswith("/playground/?code="):
        result = output or (proc.stderr or "").strip()
        reason = exclusion_reason(result)
        payload = {
            "sourceFile": record.get("sourceFile"),
            "symbol": record.get("symbol"),
            "snippetIndex": record.get("snippetIndex"),
            "result": result,
        }
        if reason:
            payload["excludedReason"] = reason
            excluded.append(payload)
        else:
            failures.append(payload)

if failures:
    print(f"Snippet validation failed for {len(failures)} snippets out of {count}.")
    print(f"Excluded {len(excluded)} known non-standalone snippets.")
    for failure in failures[:50]:
        print(json.dumps(failure, ensure_ascii=False))
    sys.exit(1)

print(f"Validated {count - len(excluded)} snippets successfully.")
print(f"Excluded {len(excluded)} known non-standalone snippets.")
PY
