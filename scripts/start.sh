#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_HOME="$(cd "${SCRIPT_DIR}/.." && pwd)"
JAR_PATH="${APP_HOME}/target/people-wiki-0.0.1-SNAPSHOT.jar"

export KNOWLEDGE_BASE_DATA_PATH="${KNOWLEDGE_BASE_DATA_PATH:-${HOME}/.knowledge-base/data/knowledge-base}"
export KNOWLEDGE_BASE_INDEX_PATH="${KNOWLEDGE_BASE_INDEX_PATH:-${HOME}/.knowledge-base/index}"
export KNOWLEDGE_BASE_IMAGES_PATH="${KNOWLEDGE_BASE_IMAGES_PATH:-${HOME}/.knowledge-base/images}"

if [[ ! -f "${JAR_PATH}" ]]; then
  echo "未找到 Jar：${JAR_PATH}"
  echo "请先执行：mvn clean package"
  exit 1
fi

java -jar "${JAR_PATH}" "$@"
