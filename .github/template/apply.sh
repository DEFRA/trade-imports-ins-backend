#!/bin/bash

set -e

repository=$1

REPO_NAME=$(echo "${repository}" | awk -F '/' '{print $2}')
REPO_NAME_PACKAGE=$(echo "$REPO_NAME" | tr '-' '.')
REPO_NAME_PATH=$(echo "$REPO_NAME" | tr '-' '/')
REPO_NAME_TITLE_CASE=$(echo "$REPO_NAME" | tr '-' ' ' | awk '{for(i=1;i<=NF;i++) $i=toupper(substr($i,1,1)) substr($i,2); print}')

echo "applying template for repository: ${repository}"
echo "  REPO_NAME:            ${REPO_NAME}"
echo "  REPO_NAME_PACKAGE:    ${REPO_NAME_PACKAGE}"
echo "  REPO_NAME_TITLE_CASE: ${REPO_NAME_TITLE_CASE}"

# Rename all occurrences in file contents.

echo "renaming cdp-java-backend-template -> ${REPO_NAME}"
find . \( -name .git -o -name .github -o -path ./target \) -prune -o -type f \
  -exec sed -i "s|cdp-java-backend-template|${REPO_NAME}|g" {} +

echo "renaming 'CDP Java Backend Template' -> '${REPO_NAME_TITLE_CASE}'"
find . \( -name .git -o -name .github -o -path ./target \) -prune -o -type f \
  -exec sed -i "s|CDP Java Backend Template|${REPO_NAME_TITLE_CASE}|g" {} +

echo "renaming package uk.gov.defra.cdp.java -> uk.gov.defra.${REPO_NAME_PACKAGE}"
find . \( -name .git -o -name .github -o -path ./target \) -prune -o -type f \
  -exec sed -i "s|uk\.gov\.defra\.cdp\.java|uk.gov.defra.${REPO_NAME_PACKAGE}|g" {} +

echo "renaming path uk/gov/defra/cdp/java -> uk/gov/defra/${REPO_NAME_PATH}"
find . \( -name .git -o -name .github -o -path ./target \) -prune -o -type f \
  -exec sed -i "s|uk/gov/defra/cdp/java|uk/gov/defra/${REPO_NAME_PATH}|g" {} +

# Move Java source directories to match the new package structure
echo "moving src/main/java/uk/gov/defra/cdp/java -> src/main/java/uk/gov/defra/${REPO_NAME_PATH}"
mkdir -p "src/main/java/uk/gov/defra/$(dirname "${REPO_NAME_PATH}")"
mv "src/main/java/uk/gov/defra/cdp/java" "src/main/java/uk/gov/defra/${REPO_NAME_PATH}"
rm -rf "src/main/java/uk/gov/defra/cdp"

echo "moving src/test/java/uk/gov/defra/cdp/java -> src/test/java/uk/gov/defra/${REPO_NAME_PATH}"
mkdir -p "src/test/java/uk/gov/defra/$(dirname "${REPO_NAME_PATH}")"
mv "src/test/java/uk/gov/defra/cdp/java" "src/test/java/uk/gov/defra/${REPO_NAME_PATH}"
rm -rf "src/test/java/uk/gov/defra/cdp"

tree src/main/java src/test/java
