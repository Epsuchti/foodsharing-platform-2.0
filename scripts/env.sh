# Shell setup for builds and tests (docs/dev-environment.md). Source it from anywhere inside the repository:
#
#   source scripts/env.sh
#
# Works in bash and zsh on Linux, macOS and Windows (Git Bash). It finds a JDK 21+, prefers the Node that Maven
# installs into client/node (the Angular CLI needs >= 24.15) and puts the Docker CLI on PATH when Docker Desktop is
# installed but not on PATH. Nothing is installed; missing tools are reported.

# `pwd` gives the shell's own path form (/c/… in Git Bash; git prints C:/…, whose colon would break PATH)
fs_env_root="$(cd "$(git rev-parse --show-toplevel 2>/dev/null || pwd)" && pwd)"

fs_java_major() {
  # prints the major version of the given java binary, or nothing
  "$1" -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -n 1
}

fs_find_jdk() {
  local candidate major
  # 1. an explicit JAVA_HOME wins when it is 21+
  if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    major="$(fs_java_major "$JAVA_HOME/bin/java")"
    if [ -n "$major" ] && [ "$major" -ge 21 ]; then printf '%s\n' "$JAVA_HOME"; return; fi
  fi
  # 2. common install locations (portable JDKs, SDKMAN, macOS, Linux packages, Windows)
  for candidate in \
    "$HOME"/.jdks/*21* "$HOME"/.jdks/*2[2-9]* \
    "$HOME"/.sdkman/candidates/java/2[1-9]* \
    /Library/Java/JavaVirtualMachines/*2[1-9]*/Contents/Home \
    /usr/lib/jvm/*2[1-9]* \
    "/c/Program Files/Eclipse Adoptium"/jdk-2[1-9]* "/c/Program Files/Java"/jdk-2[1-9]*; do
    if [ -x "$candidate/bin/java" ] || [ -x "$candidate/bin/java.exe" ]; then
      printf '%s\n' "$candidate"; return
    fi
  done
  # 3. java on PATH
  if command -v java >/dev/null 2>&1; then
    major="$(fs_java_major java)"
    if [ -n "$major" ] && [ "$major" -ge 21 ]; then
      dirname "$(dirname "$(command -v java)")"; return
    fi
  fi
}

fs_jdk="$(fs_find_jdk)"
if [ -n "$fs_jdk" ]; then
  export JAVA_HOME="$fs_jdk"
  export PATH="$JAVA_HOME/bin:$PATH"
else
  echo "[env] No JDK 21+ found. Install one (e.g. Temurin 21) or set JAVA_HOME." >&2
fi

# Node: the copy Maven installs (frontend-maven-plugin) matches the version the build uses
if [ -d "$fs_env_root/client/node" ]; then
  export PATH="$fs_env_root/client/node:$PATH"
elif ! command -v node >/dev/null 2>&1; then
  echo "[env] No Node found. Run './mvnw -DskipTests package' in server/ once (it installs client/node), or install Node 24.15+." >&2
fi

# Docker CLI of Docker Desktop on Windows is not always on PATH
if ! command -v docker >/dev/null 2>&1 && [ -d "/c/Program Files/Docker/Docker/resources/bin" ]; then
  export PATH="/c/Program Files/Docker/Docker/resources/bin:$PATH"
fi

echo "[env] java: ${JAVA_HOME:-missing} | node: $(command -v node >/dev/null 2>&1 && node --version || echo missing) | docker: $(command -v docker >/dev/null 2>&1 && echo found || echo missing)"

unset -f fs_java_major fs_find_jdk
unset fs_env_root fs_jdk
