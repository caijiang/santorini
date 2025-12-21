#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CHART_DIR="$(dirname "$SCRIPT_DIR")"
echo "脚本目录: $SCRIPT_DIR"
echo "上一级目录: $CHART_DIR"

if [ $1 = "-D" ]; then
  echo 准备删除
  kubectl delete -f "${SCRIPT_DIR}/preview/**/*.yaml" -f "${SCRIPT_DIR}/preview/*.yaml"
else
  kubectl apply -f "${SCRIPT_DIR}/preview/**/*.yaml" -f "${SCRIPT_DIR}/preview/*.yaml"
fi
