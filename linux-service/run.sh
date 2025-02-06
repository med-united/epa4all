#!/bin/sh

/usr/local/bin/promtail -config.file=/opt/epa4all/promtail/promtail.yaml &
socat -u TCP-LISTEN:4560,fork OPEN:/opt/epa4all/promtail/epa4all.log,creat,append &
export QUARKUS_CONFIG_LOCATIONS=config/application.properties
exec /usr/bin/java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=0.0.0.0:5005 -Dquarkus.profile="${QUARKUS_PROFILE:-mTLS-docker}" -jar quarkus-run.jar
