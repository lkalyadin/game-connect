#!/usr/bin/env bash
# Проверяет, что код ачивок и лидербордов не разошёлся с upstream.
#
# Логика: файлы, отвечающие за ачивки/лидерборды, должны совпадать с базой форка
# побайтово (сверка git-блобов), а в файлах, куда мы добавляли сейвы, не должно
# быть ни одной удалённой или изменённой строки — только добавления.
#
# Использование: bash scripts/verify-upstream-identical.sh [ref]
#   ref по умолчанию upstream/main (база форка, 8.0.0 / fbcc813).
# Код возврата: 0 — всё сходится, 1 — есть расхождения.

set -uo pipefail

REF="${1:-upstream/main}"

# Файлы, которые обязаны быть идентичны upstream (ачивки, лидерборды, вход).
IDENTICAL_FILES=(
  "android/src/main/java/com/openforge/capacitorgameconnect/CapacitorGameConnect.java"
  "android/src/main/java/com/openforge/capacitorgameconnect/SignInCallback.java"
  "android/src/main/java/com/openforge/capacitorgameconnect/PlayerResultCallback.java"
  "android/build.gradle"
  "android/src/main/AndroidManifest.xml"
  "ios/Plugin/CapacitorGameConnect.swift"
  "src/index.ts"
  "src/interfaces/player-score.interface.ts"
  "src/interfaces/user.interface.ts"
)

# Файлы, где допустимы только добавления (сейвы).
ADDITIVE_FILES=(
  "android/src/main/java/com/openforge/capacitorgameconnect/CapacitorGameConnectPlugin.java"
  "src/definitions.ts"
  "src/web.ts"
)

fail=0

if ! git rev-parse --verify -q "$REF" >/dev/null; then
  echo "Нет ref '$REF'. Сделайте: git fetch upstream"
  exit 1
fi

echo "База для сверки: $REF ($(git rev-parse --short "$REF"))"
echo

echo "== Должны совпадать побайтово =="
for f in "${IDENTICAL_FILES[@]}"; do
  a=$(git rev-parse "$REF:$f" 2>/dev/null)
  b=$(git rev-parse "HEAD:$f" 2>/dev/null)
  if [ -n "$a" ] && [ "$a" = "$b" ]; then
    printf '  OK        %s\n' "$f"
  else
    printf '  ИЗМЕНЁН   %s\n' "$f"
    fail=1
  fi
done
echo

echo "== Допустимы только добавления (ни одной удалённой строки) =="
for f in "${ADDITIVE_FILES[@]}"; do
  removed=$(git diff --no-color "$REF" HEAD -- "$f" | grep -E '^-' | grep -v '^---' || true)
  if [ -z "$removed" ]; then
    added=$(git diff --no-color --numstat "$REF" HEAD -- "$f" | awk '{print $1}')
    printf '  OK        %s (+%s строк)\n' "$f" "${added:-0}"
  else
    printf '  ИЗМЕНЁН   %s — удалённые/правленые строки:\n' "$f"
    printf '%s\n' "$removed" | sed 's/^/              /'
    fail=1
  fi
done
echo

if [ "$fail" -eq 0 ]; then
  echo "ИТОГ: код ачивок и лидербордов идентичен upstream, изменения только аддитивные."
else
  echo "ИТОГ: есть расхождения с upstream — смотрите строки ИЗМЕНЁН выше."
fi

exit "$fail"
