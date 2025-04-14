#!/bin/sh

#./mvnw clean install

if [ -z "$1" ]
then
 echo "Secret path not specified for Konnektor key-store"
 return 1
fi

#mvn -U dependency:copy -Dartifact=io.prometheus.jmx:jmx_prometheus_javaagent:1.0.1:jar -DoutputDirectory=rest-server/prometheus

docker volume create epa4all-webdav
docker rm epa4all
docker build --progress=plain --no-cache -t epa4all .

docker run -d --name epa4all \
  --add-host=host.docker.internal:host-gateway \
  -e SERVICEHEALTH_CLIENT_ID=cardlink_service_health2 \
  -e SHARE_PERSONAL_DATA=false \
  -e QUARKUS_PROFILE=RU \
  -e MASK_SENSITIVE=false \
  -e VSD_TEST_MODE=false \
  -e HCV_ENABLED=true \
  -p 8090:8090 -p 5005:5005 -p 8588:8588 -p 3102:3102 -p 20001:20001 -p 8787:8787 \
  -v "$1":/opt/epa4all/secret \
  -v epa4all-webdav:/opt/epa4all/webdav \
  epa4all



echo "Starting epa4all .."

# sleep 5
# curl -v -k https://localhost:8445/health --cert tls/server/trust-store/client/client.p12:changeit --cert-type P12 | jq

# shellcheck disable=SC2028
echo "docker image epa4all running. \n Status with: 'docker ps' \n Logs with: 'docker logs epa4all' \n Kill with: 'docker kill epa4all'"
