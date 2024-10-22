#!/bin/sh
export QUARKUS_CONFIG_LOCATIONS=config/application.properties
sudo /usr/bin/java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5045 -jar rest-server-1.0-SNAPSHOT-runner.jar
