#!/bin/sh
export QUARKUS_CONFIG_LOCATIONS=config/application.properties
/usr/bin/java -Dquarkus.profile=${QUARKUS_PROFILE:-mTLS-docker} -jar quarkus-run.jar
