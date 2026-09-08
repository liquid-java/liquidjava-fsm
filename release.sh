#!/usr/bin/env bash

set -euo pipefail

if [ "$#" -gt 1 ]; then
    echo "Usage: $0 [version]"
    exit 1
fi

POM="pom.xml"
CURRENT_VERSION=$(perl -0ne 'print $1 if m#<artifactId>liquidjava-fsm</artifactId>\s*<version>([^<]+)</version>#' "$POM")

if [ -z "$CURRENT_VERSION" ]; then
    echo "Could not read current version from $POM"
    exit 1
fi

VERSION=${1:-}
if [ -z "$VERSION" ]; then
    if [[ "$CURRENT_VERSION" =~ ^[0-9]+(\.[0-9]+){1,2}-SNAPSHOT$ ]]; then
        VERSION=${CURRENT_VERSION%-SNAPSHOT}
        echo "Releasing liquidjava-fsm $VERSION"
    elif [[ "$CURRENT_VERSION" =~ ^[0-9]+(\.[0-9]+){1,2}$ ]]; then
        IFS=. read -ra VERSION_PARTS <<<"$CURRENT_VERSION"
        LAST_INDEX=$((${#VERSION_PARTS[@]} - 1))
        VERSION_PARTS[$LAST_INDEX]=$((${VERSION_PARTS[$LAST_INDEX]} + 1))
        VERSION=$(IFS=.; echo "${VERSION_PARTS[*]}")
        echo "Bumping liquidjava-fsm from $CURRENT_VERSION to $VERSION"
    else
        echo "Cannot automatically bump non-numeric version: $CURRENT_VERSION"
        echo "Pass the release version explicitly."
        exit 1
    fi
fi

if ! [[ "$VERSION" =~ ^[0-9]+(\.[0-9]+){1,2}$ ]]; then
    echo "Invalid release version: $VERSION"
    echo "Expected format: 1.2.3"
    exit 1
fi

TAG="v$VERSION"

CURRENT_BRANCH=$(git branch --show-current)
if [ "$CURRENT_BRANCH" != "main" ]; then
    echo "Release must be run from main. Current branch: $CURRENT_BRANCH"
    exit 1
fi

if [ -n "$(git status --porcelain)" ]; then
    echo "Worktree must be clean before releasing."
    git status --short
    exit 1
fi

if git rev-parse "$TAG" >/dev/null 2>&1; then
    echo "Tag already exists: $TAG"
    exit 1
fi

mvn -B --fail-fast -Dgpg.skip=true -Dmaven.deploy.skip=true clean verify

perl -0pi -e 's#(<artifactId>liquidjava-fsm</artifactId>\s*<version>)[^<]+(</version>)#${1}'"$VERSION"'${2}#' "$POM"

if git diff --quiet -- "$POM"; then
    echo "$POM is already at version $VERSION"
    exit 1
fi

git add "$POM"
git commit -m "Release liquidjava-fsm $VERSION"
git tag "$TAG"
git push origin main
git push origin "$TAG"

echo "Created and pushed $TAG. GitHub Actions will publish liquidjava-fsm to Maven Central."
