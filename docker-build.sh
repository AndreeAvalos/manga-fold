#!/usr/bin/env bash
# Compila el APK de debug dentro de un contenedor Docker, sin tocar el host.
# Requisitos: Docker Desktop abierto y corriendo.
#
# Uso:
#   ./docker-build.sh
#
# El APK resultante queda en app/build/outputs/apk/debug/app-debug.apk
set -euo pipefail
cd "$(dirname "$0")"

IMAGE_NAME="mangafold-builder"

echo "==> Construyendo la imagen Docker (solo tarda la primera vez)..."
docker build -t "$IMAGE_NAME" -f Dockerfile .

echo "==> Compilando dentro del contenedor..."
docker run --rm \
  -v "$(pwd)":/project \
  -v "mangafold-gradle-cache:/root/.gradle" \
  -w /project \
  "$IMAGE_NAME"

echo "==> Listo. APK en app/build/outputs/apk/debug/app-debug.apk"
