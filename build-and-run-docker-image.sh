#!/bin/sh

#./mvnw clean install

if [ -z "$1" ]
then
 echo "Secret path not specified for Konnektor key-store"
 return 1
fi

docker rm epa4all
docker build --progress=plain --no-cache -t epa4all .

docker run -d --name epa4all \
  -p 8090:8090 \
  -v "$1":/opt/epa4all/secret epa4all

# shellcheck disable=SC2028
echo "docker image epa4all running. \n Status with: 'docker ps' \n Logs with: 'docker logs epa4all' \n Kill with: 'docker kill epa4all'"
