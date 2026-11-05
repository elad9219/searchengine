#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"

conflicts=()
while IFS= read -r variable; do
    case "$variable" in
        SPRING_*|ELASTICSEARCH_*|JAVA_TOOL_OPTIONS|JDK_JAVA_OPTIONS|_JAVA_OPTIONS|MAVEN_OPTS|MAVEN_ARGS)
            conflicts+=("$variable")
            ;;
    esac
done < <(compgen -e)

if [ "${#conflicts[@]}" -gt 0 ]; then
    printf '%s\n' "Local startup blocked: these environment variables may override local settings:" >&2
    printf '  %s\n' "${conflicts[@]}" >&2
    printf '%s\n' "Use a terminal without these overrides. Values have not been printed." >&2
    exit 1
fi

exec mvn spring-boot:run \
    "-Dspring-boot.run.arguments=--spring.config.location=file:./dev/application-local.properties --spring.profiles.active=local" \
    "-Dspring-boot.run.jvmArguments=-Xms128m -Xmx512m"