#!/usr/bin/env bash
# Compila o projeto e gera dist/servidor.jar e dist/cliente.jar (requer JDK 17 ou mais novo).
set -euo pipefail
cd "$(dirname "$0")"

rm -rf -- out dist
mkdir -p out dist

# A partir dos dois "main", o javac encontra e compila todas as outras classes pelo --source-path.
javac --release 17 -encoding UTF-8 -d out --source-path src \
    src/br/edu/utfpr/sdcontas/servidor/ServidorApp.java \
    src/br/edu/utfpr/sdcontas/cliente/ClienteApp.java

jar --create --file dist/servidor.jar --main-class br.edu.utfpr.sdcontas.servidor.ServidorApp -C out .
jar --create --file dist/cliente.jar --main-class br.edu.utfpr.sdcontas.cliente.ClienteApp -C out .

echo "Pronto:"
echo "  java -jar dist/servidor.jar             (janela)"
echo "  java -jar dist/servidor.jar --console   (terminal)"
echo "  java -jar dist/cliente.jar"
