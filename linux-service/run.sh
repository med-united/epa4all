#!/bin/sh
export QUARKUS_CONFIG_LOCATIONS=config/application.properties
sudo /usr/bin/java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5045 -Dquarkus.profile=proxy -jar quarkus-run.jar
