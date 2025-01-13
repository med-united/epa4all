#!/bin/sh
export QUARKUS_CONFIG_LOCATIONS=config
/usr/bin/java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5045 -e QUARKUS_PROFILE=RU -jar quarkus-run.jar
