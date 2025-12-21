#!/bin/bash

# helm plugin install https://github.com/helm-unittest/helm-unittest.git

PLUGIN_NAME="unittest"
PLUGIN_REPO="https://github.com/helm-unittest/helm-unittest.git"

if helm plugin list | awk '{print $1}' | grep -x "$PLUGIN_NAME"; then
  echo "Helm plugin '$PLUGIN_NAME' already installed."
else
  echo "Installing Helm plugin '$PLUGIN_NAME'..."
  helm plugin install "$PLUGIN_REPO"
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ -d "${SCRIPT_DIR}/charts" ]; then
  echo "charts is exist"
else
  helm dependency build ${SCRIPT_DIR}
fi

helm unittest ${SCRIPT_DIR}
