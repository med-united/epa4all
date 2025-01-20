#!/bin/sh
export QUARKUS_CONFIG_LOCATIONS=config
/usr/bin/java -Dquarkus.profile=mTLS-docker -jar quarkus-run.jar
