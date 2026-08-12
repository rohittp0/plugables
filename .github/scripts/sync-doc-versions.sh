#!/usr/bin/env bash

set -euo pipefail

: "${R2_PUBLIC_URL:?R2_PUBLIC_URL is required}"

output_path="${1:-docs/versions.json}"
plugins=(
  auto-assert
  branchmark
  codeview
  proto-extended
  typed-events
  viewmodel-stub
)

versions='{}'
for plugin in "${plugins[@]}"; do
  metadata_url="${R2_PUBLIC_URL%/}/com/rohittp/plugables/${plugin}/maven-metadata.xml"
  metadata=$(curl \
    --fail \
    --silent \
    --show-error \
    --retry 12 \
    --retry-all-errors \
    --retry-delay 5 \
    "$metadata_url")
  version=$(sed -nE 's|.*<release>([^<]+)</release>.*|\1|p' <<< "$metadata")

  if [[ -z "$version" ]]; then
    echo "::error::No release version in ${metadata_url}" >&2
    exit 1
  fi

  versions=$(jq \
    --arg plugin "$plugin" \
    --arg version "$version" \
    '. + {($plugin): $version}' \
    <<< "$versions")
done

temporary_output=$(mktemp)
jq --sort-keys . <<< "$versions" > "$temporary_output"
mv "$temporary_output" "$output_path"

echo "Updated ${output_path} from published R2 Maven metadata."
