#!/bin/bash
# Beit Mama - Run in browser (GitHub Codespaces)
# Serves the web app on port 8080 and opens a public URL.
cd "$(dirname "$0")"

if [ ! -f pom.xml ]; then echo "Run from project root"; exit 1; fi

# Compile with headless-safe settings (skip Swing GUI, run web server only via system property)
mvn -q compile -Dmaven.compiler.release=17

echo "=== Starting Beit Mama Web Server on port 8080 ==="
exec mvn -q exec:java \
  -Dexec.mainClass=com.beitmama.Main \
  -Dmaven.compiler.release=17 \
  -Dexec.args="-Djava.awt.headless=true -Dbeitmama.port=8080"
