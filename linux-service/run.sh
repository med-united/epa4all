#!/bin/sh
export QUARKUS_CONFIG_LOCATIONS=config/application.properties
/usr/bin/java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=0.0.0.0:5005 -Dquarkus.profile=${QUARKUS_PROFILE:-mTLS-docker} -jar quarkus-run.jar
