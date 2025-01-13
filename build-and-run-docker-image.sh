#!/bin/sh

#./mvnw clean install

docker rm epa4all
docker build --progress=plain --no-cache -t epa4all .

docker run -d --name epa4all \
  -p 8090:8090 \
  -v /Users/bona/Work/ere.health/secret:/opt/epa4all/secret epa4all

echo "docker image epa4all running. \n Status with: 'docker ps' \n Logs with: 'docker logs epa4all' \n Kill with: 'docker kill epa4all'"
