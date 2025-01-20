#!/bin/sh
export QUARKUS_CONFIG_LOCATIONS=config/application.properties
/usr/bin/java -Dquarkus.profile=mTLS-docker -jar quarkus-run.jar
