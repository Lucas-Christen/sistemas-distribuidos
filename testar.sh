#!/usr/bin/env bash
# Compila código + testes e roda todos os testes (unidade, integração, rede e concorrência).
set -euo pipefail
cd "$(dirname "$0")"

rm -rf -- out-teste
mkdir -p out-teste
javac --release 17 -encoding UTF-8 -d out-teste --source-path "src:teste" \
    teste/br/edu/utfpr/sdcontas/TodosOsTestes.java
java -Djava.awt.headless=true -cp out-teste br.edu.utfpr.sdcontas.TodosOsTestes
