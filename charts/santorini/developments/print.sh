#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CHART_DIR="$(dirname "$SCRIPT_DIR")"
echo "脚本目录: $SCRIPT_DIR"
echo "上一级目录: $CHART_DIR"

helm install release-name $CHART_DIR -f "${SCRIPT_DIR}/print_values.yaml" --create-namespace -n kube-santorini --dry-run --debug > "${SCRIPT_DIR}/../preview.output"