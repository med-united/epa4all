#!/bin/bash
sudo git pull
sudo mvn clean package -Dquarkus.package.type=fast-jar -DskipTests
sudo systemctl stop epa4all
sudo rm -r /opt/epa4all/app/*
sudo cp rest-server/target/quarkus-app/app/* /opt/epa4all/app
sudo rm -r /opt/epa4all/quarkus/*
sudo cp -r rest-server/target/quarkus-app/quarkus/* /opt/epa4all/quarkus
sudo rm -r /opt/epa4all/lib/*
sudo cp -r rest-server/target/quarkus-app/lib/* /opt/epa4all/lib
sudo bash -c 'echo "$(date --iso-8601=s) $(git rev-parse --verify HEAD)" >> /opt/epa4all/update-linux-deployments.log'
sleep 5
sudo systemctl start epa4all
sudo systemctl status epa4all
