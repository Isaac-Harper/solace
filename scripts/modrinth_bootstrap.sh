#!/usr/bin/env bash
# One-time Modrinth setup: creates the project (slug `solace-mod`), uploads icon.png, and pins the
# returned project id into gradle.properties as `modrinth_id`. The page body comes from README.md.
# Needs MODRINTH_TOKEN in the environment or in .env (PAT scopes: Create projects, Write projects,
# Create versions). After this, `./gradlew publishMods` uploads the jar as version 1.0.0+<mc>.
set -euo pipefail
cd "$(dirname "$0")/.."

if [ -z "${MODRINTH_TOKEN:-}" ] && [ -f .env ]; then
  set -a; source .env; set +a
fi
if [ -z "${MODRINTH_TOKEN:-}" ]; then
  echo "MODRINTH_TOKEN is not set (put it in .env). Create one at https://modrinth.com/settings/pats" >&2
  exit 1
fi

if grep -q '^modrinth_id=' gradle.properties; then
  echo "modrinth_id already set in gradle.properties; nothing to do." >&2
  exit 0
fi

SUMMARY="A per-player middleground between Survival and Creative: one player gets a safe, no-combat state with a dial up to creative-lite, while everyone else keeps full vanilla survival on the same world."

DATA=$(jq -n --arg summary "$SUMMARY" --rawfile body README.md '{
  slug: "solace-mod",
  title: "Solace",
  description: $summary,
  categories: ["game-mechanics", "utility"],
  client_side: "optional",
  server_side: "required",
  body: $body,
  license_id: "MIT",
  source_url: "https://github.com/Isaac-Harper/solace",
  issues_url: "https://github.com/Isaac-Harper/solace/issues",
  is_draft: true,
  initial_versions: []
}')

# --form-string, not -F: the body contains `;`, which -F would parse as a part option separator.
RESP=$(curl -sS --fail-with-body -X POST \
  -H "Authorization: $MODRINTH_TOKEN" \
  --form-string "data=$DATA" \
  https://api.modrinth.com/v2/project)
ID=$(echo "$RESP" | jq -r .id)
echo "created project: https://modrinth.com/mod/solace-mod (id $ID)"

curl -sS --fail-with-body -X PATCH \
  -H "Authorization: $MODRINTH_TOKEN" \
  -H "Content-Type: image/png" \
  --data-binary @icon.png \
  "https://api.modrinth.com/v2/project/$ID/icon?ext=png" > /dev/null
echo "icon uploaded"

# Pin the id for the publishMods block and the CI body sync.
sed -i '' "s|^#modrinth_id=$|modrinth_id=$ID|" gradle.properties
grep -q "^modrinth_id=$ID$" gradle.properties || echo "modrinth_id=$ID" >> gradle.properties
echo "modrinth_id=$ID written to gradle.properties"

echo
echo "Next: ./gradlew publishMods   (uploads the jar; the draft goes public once you hit"
echo "Publish on the project page). For CI: gh secret set MODRINTH_TOKEN"
