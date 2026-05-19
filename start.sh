#!/usr/bin/env bash
# Launcher for the CSA Minecraft project on macOS.
#
# Auto-installs Homebrew, JDK 17, and Gradle if missing, then runs the game.
# The Gradle `application` plugin already injects -XstartOnFirstThread on macOS
# via build.gradle, so we don't need to add it here.
#
# Skip auto-install with: NO_INSTALL=1 ./start.sh

set -euo pipefail

cd "$(dirname "$0")"

NO_INSTALL="${NO_INSTALL:-0}"

# ── platform sanity check ────────────────────────────────────────────────
if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "error: start.sh is for macOS only; detected $(uname -s)." >&2
  exit 1
fi

confirm() {
  # confirm "message"  → exits non-zero if user says no (defaults to yes)
  if [[ "${ASSUME_YES:-0}" == "1" ]]; then return 0; fi
  read -r -p "$1 [Y/n] " ans
  [[ -z "$ans" || "$ans" =~ ^[Yy] ]]
}

# ── homebrew ─────────────────────────────────────────────────────────────
ensure_brew() {
  if command -v brew >/dev/null 2>&1; then return 0; fi
  if [[ "$NO_INSTALL" == "1" ]]; then
    echo "error: Homebrew not installed and NO_INSTALL=1." >&2
    exit 1
  fi
  echo "Homebrew is required to install JDK 17 and Gradle."
  confirm "Install Homebrew now?" || { echo "aborted." >&2; exit 1; }
  /bin/bash -c \
    "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
  # add brew to PATH for this session (Apple Silicon vs Intel locations)
  if [[ -x /opt/homebrew/bin/brew ]]; then
    eval "$(/opt/homebrew/bin/brew shellenv)"
  elif [[ -x /usr/local/bin/brew ]]; then
    eval "$(/usr/local/bin/brew shellenv)"
  fi
}

# ── jdk 17 ───────────────────────────────────────────────────────────────
find_java17() {
  # 1) JAVA_HOME if it points to a 17+ JDK
  if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
    local ver
    ver="$("$JAVA_HOME/bin/java" -version 2>&1 | head -n1 | sed -E 's/.*"([0-9]+).*/\1/')"
    if [[ "$ver" =~ ^[0-9]+$ ]] && (( ver >= 17 )); then
      echo "$JAVA_HOME"
      return 0
    fi
  fi
  # 2) macOS java_home picker
  if command -v /usr/libexec/java_home >/dev/null 2>&1; then
    if /usr/libexec/java_home -v 17+ 2>/dev/null; then return 0; fi
  fi
  # 3) brew openjdk@17 location (not always symlinked into /Library)
  for c in /opt/homebrew/opt/openjdk@17 /usr/local/opt/openjdk@17; do
    if [[ -x "$c/bin/java" ]]; then
      echo "$c"
      return 0
    fi
  done
  return 1
}

ensure_java() {
  if JH="$(find_java17)"; then
    export JAVA_HOME="$JH"
    return 0
  fi
  if [[ "$NO_INSTALL" == "1" ]]; then
    echo "error: JDK 17 not found and NO_INSTALL=1." >&2
    exit 1
  fi
  ensure_brew
  echo "Installing openjdk@17 via Homebrew…"
  brew install openjdk@17

  # Symlink so /usr/libexec/java_home picks it up (needs sudo).
  local brew_jdk
  brew_jdk="$(brew --prefix openjdk@17)/libexec/openjdk.jdk"
  local target="/Library/Java/JavaVirtualMachines/openjdk-17.jdk"
  if [[ -d "$brew_jdk" && ! -e "$target" ]]; then
    if confirm "Symlink openjdk@17 into $target (requires sudo)?"; then
      sudo ln -sfn "$brew_jdk" "$target"
    fi
  fi

  if JH="$(find_java17)"; then
    export JAVA_HOME="$JH"
  else
    echo "error: installed openjdk@17 but still can't locate it." >&2
    exit 1
  fi
}

# ── gradle ───────────────────────────────────────────────────────────────
ensure_gradle() {
  if [[ -x "./gradlew" ]]; then
    GRADLE_CMD=("./gradlew")
    return 0
  fi
  if command -v gradle >/dev/null 2>&1; then
    GRADLE_CMD=("gradle")
    return 0
  fi
  if [[ "$NO_INSTALL" == "1" ]]; then
    echo "error: gradle not found and NO_INSTALL=1." >&2
    exit 1
  fi
  ensure_brew
  echo "Installing gradle via Homebrew…"
  brew install gradle
  GRADLE_CMD=("gradle")
}

# ── main ─────────────────────────────────────────────────────────────────
ensure_java
GRADLE_CMD=()
ensure_gradle

JAVA_VERSION_RAW="$("$JAVA_HOME/bin/java" -version 2>&1 | head -n1)"
echo "→ JAVA_HOME: $JAVA_HOME"
echo "→ Java:      $JAVA_VERSION_RAW"
echo "→ Gradle:    ${GRADLE_CMD[*]}"
echo "→ Launching minecraft (Ctrl-C to quit) …"
echo

exec "${GRADLE_CMD[@]}" --console=plain run "$@"
