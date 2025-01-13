#!/bin/sh
export QUARKUS_CONFIG_LOCATIONS=config
/usr/bin/java -Dquarkus.profile=RU -jar quarkus-run.jar
