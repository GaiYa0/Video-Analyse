#!/usr/bin/env bash
set -euo pipefail
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
java -version
cd /opt/wvp-GB28181-pro
mvn -DskipTests package
echo "BUILD_OK"
ls -lh target/wvp-pro-*.jarr | grep -v original || ls -lh target/*.jar
