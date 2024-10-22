#!/bin/bash
git pull
mvn clean package -Dquarkus.package.type=uber-jar -DskipTests
sudo systemctl stop epa4all
sudo rm -r /opt/epa4all/rest-server-1.0-SNAPSHOT-runner.jar
sudo cp -r rest-server/target/rest-server-1.0-SNAPSHOT-runner.jar /opt/epa4all/
sudo bash -c 'echo "$(date --iso-8601=s) $(git rev-parse --verify HEAD)" >> /opt/epa4all/update-linux-deployments.log'
sleep 5
sudo systemctl start epa4all
sudo systemctl status epa4all
