#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CHART_DIR="$(dirname "$SCRIPT_DIR")"
echo "脚本目录: $SCRIPT_DIR"
echo "上一级目录: $CHART_DIR"
# helm install [NAME] [CHART] [flags]

# helm install release-name $CHART_DIR -f "${SCRIPT_DIR}/staging_values.yaml" --dry-run --debug > "${SCRIPT_DIR}/../preview.output"
# helm install release-name $CHART_DIR -f "${SCRIPT_DIR}/staging_values.yaml"

# helm upgrade --reuse-values --install release-name . --force
helm upgrade -f "${SCRIPT_DIR}/staging_values.yaml" --install release-name --create-namespace -n kube-santorini $CHART_DIR
