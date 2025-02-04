#!/bin/sh

#PIPE_FILE=/tmp/quarkus-logs
#rm -f $PIPE_FILE
#mkfifo $PIPE_FILE

#/usr/local/bin/promtail -config.file=/etc/promtail/config.yml < $PIPE_FILE &

/usr/local/bin/promtail -config.file=/etc/promtail/config.yml &

export QUARKUS_CONFIG_LOCATIONS=config/application.properties
exec /usr/bin/java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=0.0.0.0:5005 -Dquarkus.profile="${QUARKUS_PROFILE:-mTLS-docker}" -jar quarkus-run.jar

# | /usr/local/bin/promtail --stdin -config.file=/etc/promtail/config.yml > /var/log/promtail.log 2>&1
# | /usr/local/bin/promtail --stdin -config.file=/etc/promtail/config.yml > /var/log/promtail.log 2>&1
#  > $PIPE_FILE 2>&1
