#!/bin/sh

/usr/local/bin/promtail -config.file=/opt/epa4all/promtail/promtail.yaml -config.expand-env=true &
socat -u TCP-LISTEN:4560,fork OPEN:/opt/epa4all/promtail/epa4all.log,creat,append &
export QUARKUS_CONFIG_LOCATIONS=config/application.properties
exec /usr/bin/java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=0.0.0.0:5005 \
 -Dcom.sun.management.jmxremote \
 -Dcom.sun.management.jmxremote.authenticate=false \
 -Dcom.sun.management.jmxremote.ssl=false \
 -Dcom.sun.management.jmxremote.port=8787 \
 -Dcom.sun.management.jmxremote.rmi.port=8787 \
 -Djava.rmi.server.hostname=127.0.0.1 -javaagent:jmx_prometheus_javaagent-1.0.1.jar=20001:prometheus.yaml \
 -Dquarkus.profile="${QUARKUS_PROFILE:-mTLS-docker}" -jar quarkus-run.jar
