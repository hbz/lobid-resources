#!/bin/sh
cd ../..
git clone https://github.com/hbz/metafacture-core.git
cd metafacture-core
git pull
git checkout 5.0.1-hbz
git pull
./gradlew install
cd ..
git clone https://github.com/jsonld-java/jsonld-java.git
cd jsonld-java
git pull
mvn clean install -DskipTests=true
