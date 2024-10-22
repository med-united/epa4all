#!/bin/bash
sudo systemctl stop epa4all
sudo systemctl disable epa4all.service
sudo rm -R /opt/epa4all
sudo rm /etc/systemd/system/epa4all.service
echo "epa4all successfully uninstalled"
