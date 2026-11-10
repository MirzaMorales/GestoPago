#!/usr/bin/env bash
# Construye la web de Flutter en un Static Site de Render (Render no trae Flutter instalado).
#   Root Directory:    app_flutter
#   Build Command:     bash render-build.sh
#   Publish Directory: build/web
# Variable opcional en Render: API_URL (por defecto el backend de producción).
set -euo pipefail

FLUTTER_VERSION="${FLUTTER_VERSION:-3.47.6}"
FLUTTER_DIR="${HOME}/flutter-${FLUTTER_VERSION}"

if [ ! -x "${FLUTTER_DIR}/bin/flutter" ]; then
  echo "Descargando Flutter ${FLUTTER_VERSION}..."
  git clone --depth 1 --branch "${FLUTTER_VERSION}" https://github.com/flutter/flutter.git "${FLUTTER_DIR}"
fi
export PATH="${FLUTTER_DIR}/bin:${PATH}"

flutter config --no-analytics --enable-web >/dev/null
flutter --version
flutter pub get
flutter build web --release --dart-define=API_URL="${API_URL:-https://gestopago-bmb0.onrender.com}"
