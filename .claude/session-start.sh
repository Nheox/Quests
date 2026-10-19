#!/bin/bash
# Makes sure a fresh session can actually build and test this project.
#
# The two things that are not obvious and cost a session's first ten minutes
# otherwise: the build needs JDK 21, and `packageDeb` silently skips unless
# fakeroot is installed (the reason is only visible in build/compose/logs/).
set -u

echo "Quests — session setup"

if command -v java >/dev/null 2>&1; then
  echo "  java: $(java -version 2>&1 | head -1)"
else
  echo "  java: NOT FOUND — the build needs JDK 21"
fi

if ! command -v fakeroot >/dev/null 2>&1; then
  echo "  fakeroot: missing — ./gradlew packageDeb will skip the bundler."
  echo "            install with: apt-get install -y fakeroot"
else
  echo "  fakeroot: present"
fi

if ! command -v Xvfb >/dev/null 2>&1; then
  echo "  Xvfb: missing — the app cannot be launched headlessly for a smoke test."
else
  echo "  Xvfb: present (run the app with: xvfb-run -a ./gradlew :composeApp:run)"
fi

echo "  tests:   ./gradlew :composeApp:desktopTest"
echo "  run:     ./gradlew :composeApp:run"
echo "  package: ./gradlew packageDeb"
exit 0
