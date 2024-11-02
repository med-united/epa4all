#!/bin/bash
mkdir -p /opt/epa4all/webdav
git pull
mvn clean package -Dquarkus.package.type=fast-jar -DskipTests
sudo cp -r rest-server/target/quarkus-app/* /opt/epa4all/
sudo mkdir -p /opt/epa4all/config/konnektoren/8588
sudo cp rest-server/src/main/resources/application.properties /opt/epa4all/config
cp rest-server/config/konnektoren/8588/* /opt/epa4all/config/konnektoren/8588
cp run.sh /opt/epa4all/
cp epa4all.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable epa4all.service
sudo systemctl start epa4all
sudo systemctl status epa4all
