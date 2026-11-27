#!/usr/bin/env bash
# Checks that JitPack built a release tag and serves every module's jar.
#
#   .github/scripts/jitpack-check.sh v2.7.1
#
# Run from the repository root: the modules are read from the <module> lines of pom.xml. It asks JitPack for the api
# module's pom, which starts the build if nobody asked for the tag yet, waits for JitPack's build API to say "ok" or
# "Error" (https://docs.jitpack.io/api/), then downloads each module's jar. It exits 1, with the build log's URL, when
# JitPack could not build the tag or a jar is missing, and 2 when JitPack did not finish in time.
#
# The result goes to $GITHUB_STEP_SUMMARY in a workflow run, and to the terminal otherwise.
# JITPACK_ATTEMPTS and JITPACK_INTERVAL (seconds) bound the wait: 40 x 30 s by default, longer than the 15 minutes
# JitPack gives a build. JITPACK_URL points the script at another server, for testing.
set -uo pipefail

tag=${1:?usage: jitpack-check.sh <tag>}
group=com.github.MohamedSalah-13
repo=smart-report-plugin
jitpack=${JITPACK_URL:-https://jitpack.io}
attempts=${JITPACK_ATTEMPTS:-40}
interval=${JITPACK_INTERVAL:-30}
# The summary is written to fd 3.
if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then exec 3>>"$GITHUB_STEP_SUMMARY"; else exec 3>&1; fi

files=$jitpack/${group//.//}/$repo # https://jitpack.io/com/github/MohamedSalah-13/smart-report-plugin
api=$jitpack/api/builds/$group/$repo/$tag
log=$files/$tag/build.log

command -v jq >/dev/null || { echo "jq is needed to read JitPack's answers." >&2; exit 2; }
mapfile -t modules < <(sed -n 's:.*<module>\(.*\)</module>.*:\1:p' pom.xml 2>/dev/null)
if [ ${#modules[@]} -eq 0 ]; then
  echo "No <module> lines in ./pom.xml: run this from the repository root." >&2
  exit 2
fi

# Asks for the api module's pom. JitPack builds a tag the first time one of its files is requested and holds the
# request until the build ends; the build goes on after the request gives up.
request_build() {
  curl -s -o /dev/null -m 20 "$files/${modules[0]}/$tag/${modules[0]}-$tag.pom" || true
}

echo "Asking JitPack for $tag"
request_build

status=unknown
for ((i = 1; i <= attempts; i++)); do
  json=$(curl -sS -m 30 "$api") || json=
  status=$(jq -r '.status // "unknown"' <<<"$json" 2>/dev/null) || status=unknown
  [ -n "$status" ] || status=unknown
  echo "[$i/$attempts] $tag: $status"
  case ${status,,} in
    ok | error* | fail*) break ;;
    none) request_build ;; # not started yet: ask again
  esac
  [ "$i" -lt "$attempts" ] && sleep "$interval"
done

{
  echo "## JitPack: \`$tag\`"
  echo
} >&3

case ${status,,} in
  ok) ;;
  error* | fail*)
    message=$(jq -r '.message // ""' <<<"$json")
    echo "::error title=JitPack could not build $tag::$message Build log: $log"
    {
      echo ":x: JitPack could not build \`$tag\`: $message"
      echo
      echo "Build log: $log"
      echo
      # Maven's own error lines, without its blank lines and advice.
      errors=$(curl -s -m 30 "$log" | grep '^\[ERROR\] [^ ]' | grep -v -E 'Re-run Maven|full stack trace|For more information|\[Help [0-9]+\] http' | head -n 5)
      if [ -n "$errors" ]; then
        echo '```'
        echo "$errors"
        echo '```'
        echo
      fi
      echo "Do not move or delete the tag: fix the build and release the next patch version (RELEASING.md)."
    } >&3
    exit 1
    ;;
  *)
    echo "::error title=JitPack did not finish $tag::Last status after $attempts checks: $status. $api"
    {
      echo ":hourglass: JitPack did not finish \`$tag\` in time. Last status: \`$status\`."
      echo
      echo "Build API: $api"
      echo
      echo "Build log, once there is one: $log"
    } >&3
    exit 2
    ;;
esac

commit=$(jq -r '.commit // ""' <<<"$json")
rows=()
missing=0
for module in "${modules[@]}"; do
  jar=$files/$module/$tag/$module-$tag.jar
  for try in 1 2 3; do
    result=$(curl -s -L -o /dev/null -m 120 -w '%{http_code} %{size_download}' "$jar") || result="000 0"
    [ "${result%% *}" = 200 ] && break
    [ "$try" -lt 3 ] && sleep 10
  done
  code=${result%% *}
  size=${result##* }
  echo "$module: $code ($size bytes)"
  if [ "$code" = 200 ]; then
    rows+=("| \`$module\` | 200 | $size bytes |")
  else
    rows+=("| \`$module\` | :x: $code | |")
    echo "::error title=JitPack does not serve $module::$jar answered $code"
    missing=1
  fi
done

{
  if [ "$missing" -eq 0 ]; then
    echo "JitPack built \`$tag\` from ${commit:0:10} and serves every module's jar."
  else
    echo ":x: JitPack built \`$tag\` from ${commit:0:10}, but does not serve every module's jar. Build log: $log"
  fi
  echo
  echo "| Module | Jar | Size |"
  echo "|---|---|---|"
  printf '%s\n' "${rows[@]}"
  echo
} >&3
[ "$missing" -eq 0 ] || exit 1

baseline=$(sed -n 's:.*<api.baseline.version>\(.*\)</api.baseline.version>.*:\1:p' pom.xml)
if [ -n "$baseline" ] && [ "$baseline" != "$tag" ]; then
  echo "Next: move \`api.baseline.version\` in \`pom.xml\` from \`$baseline\` to \`$tag\` on master (RELEASING.md)." >&3
fi
