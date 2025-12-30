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

mkdir -p ${SCRIPT_DIR}/files

if [ ! -f "${SCRIPT_DIR}/files/nacos_3.0.2.sql" ]; then
  wget -O ${SCRIPT_DIR}/files/nacos_3.0.2.sql https://raw.githubusercontent.com/alibaba/nacos/3.0.2/distribution/conf/mysql-schema.sql
fi

helm unittest ${SCRIPT_DIR}
