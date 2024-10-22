#!/bin/bash
mkdir /opt/epa4all/
git pull
mvn clean package -Dquarkus.package.type=uber-jar -DskipTests
cp rest-server/target/rest-server-1.0-SNAPSHOT-runner.jar /opt/epa4all/
mkdir -p /opt/epa4all/config/konnektoren/8588
cp rest-server/src/main/resources/application.properties /opt/epa4all/config
cp rest-server/config/konnektoren/8588/* /opt/epa4all/config/konnektoren/8588
cp run.sh /opt/epa4all/
cp epa4all.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable epa4all.service
sudo systemctl start epa4all
sudo systemctl status epa4all
