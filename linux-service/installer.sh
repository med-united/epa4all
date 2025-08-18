#!/bin/bash

git pull
mvn clean package -Dquarkus.package.type=fast-jar -DskipTests

sudo mkdir -p /opt/epa4all/webdav
sudo mkdir -p /opt/epa4all/prometheus
sudo mkdir -p /opt/epa4all/config/konnektoren/8588
sudo cp -r rest-server/target/quarkus-app/* /opt/epa4all/
sudo cp rest-server/src/main/resources/application.properties /opt/epa4all/config
sudo cp rest-server/config/konnektoren/8588/* /opt/epa4all/config/konnektoren/8588

mvn -U dependency:copy -Dartifact=io.prometheus.jmx:jmx_prometheus_javaagent:1.0.1:jar -DoutputDirectory=/opt/epa4all
sudo cp rest-server/src/main/resources/prometheus.yaml /opt/epa4all
sudo cp rest-server/prometheus/prometheus.jks /opt/epa4all/prometheus
sudo cp run.sh /opt/epa4all/
sudo cp epa4all.service /etc/systemd/system/

sudo systemctl daemon-reload
sudo systemctl enable epa4all.service
sudo systemctl start epa4all
sudo systemctl status epa4all
