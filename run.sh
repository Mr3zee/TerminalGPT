#!/bin/zsh

./gradlew fatJar && java -jar build/libs/test_proj-1.0-SNAPSHOT.jar
