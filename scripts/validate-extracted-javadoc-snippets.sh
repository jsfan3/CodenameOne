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
import subprocess
import sys
import tempfile

jsonl_path = pathlib.Path(sys.argv[1])
failures = []
count = 0

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
        failures.append(
            {
                "sourceFile": record.get("sourceFile"),
                "symbol": record.get("symbol"),
                "snippetIndex": record.get("snippetIndex"),
                "result": output or (proc.stderr or "").strip(),
            }
        )

if failures:
    print(f"Snippet validation failed for {len(failures)} snippets out of {count}.")
    for failure in failures[:50]:
        print(json.dumps(failure, ensure_ascii=False))
    sys.exit(1)

print(f"Validated {count} snippets successfully.")
PY
