#!/bin/sh

#./mvnw clean install

if [ -z "$1" ]
then
 echo "Secret path not specified for Konnektor key-store"
 return 1
fi

docker volume create epa4all-webdav
docker rm epa4all
docker build --progress=plain --no-cache -t epa4all .

docker run -d --name epa4all \
  -p 8090:8090 -p 8443:8443 -p 8588:8588 \
  -v "$1":/opt/epa4all/secret epa4all \
  -v epa4all-webdav:/opt/epa4all/webdav epa4all

echo "Starting epa4all .."

sleep 5

curl -v -k https://localhost:8443/health --cert tls/server/trust-store/client/client.p12:changeit --cert-type P12 | jq

# shellcheck disable=SC2028
echo "docker image epa4all running. \n Status with: 'docker ps' \n Logs with: 'docker logs epa4all' \n Kill with: 'docker kill epa4all'"
