#!/bin/bash

echo "----------------------------------------------------------------------------------------------"
echo "-----------------------------  Welcome to the EPA4All Installer  -----------------------------"
echo "----------------------------------------------------------------------------------------------"

# STEP 1: Creating the epa4all.properties file

CONFIG_FILE="epa4all.properties"
CONFIG_URL="https://raw.githubusercontent.com/med-united/epa4all/main/production_deployment/epa4all.properties"

echo "EPA4All Installer: STEP 1: Configuring EPA4All"
if [ ! -f "$CONFIG_FILE" ]; then
    echo "EPA4All Installer: Couldn't find epa4all.properties in current working directory"
    echo "EPA4All Installer: Downloading default epa4all.properties ..."
    curl -o "$CONFIG_FILE" "$CONFIG_URL" || {
        echo "EPA4All Installer: ERROR: Could not download epa4all.properties"
        exit 1
    }
    echo "EPA4All Installer: epa4all.properties downloaded successfully"
    echo "EPA4All Installer: Please edit epa4all.properties first and run the installer again"
    exit 0
else
    echo "EPA4All Installer: Found epa4all.properties in current working directory"
    read -p "EPA4All Installer: Have you already edited epa4all.properties? (y/n): " answer
    if [ "$answer" != "y" ]; then
        echo "EPA4All Installer: Please edit epa4all.properties first and run the installer again"
        exit 0
    fi
fi

echo "EPA4All Installer: Current configuration:"
echo "-----------------------------------  epa4all.properties START  -------------------------------"
cat "$CONFIG_FILE"
echo
echo "-----------------------------------  epa4all.properties END    -------------------------------"

read -p "EPA4All Installer: Do you want to install EPA4All with this configuration? (y/n): " install
if [ "$install" != "y" ]; then
    echo "Installation cancelled"
    exit 0
fi

# STEP 2: Creating the configuration directory structure

echo
echo "EPA4All Installer: STEP 2: Create configuration directory structure"

if [ -d "epa4all_config/secret" ]; then
    read -p "EPA4All Installer: Directory epa4all_config/secret already exists. Override? (y/n): " override
    if [ "$override" == "y" ]; then
        rm -rf "epa4all_config/secret"
        mkdir -p "epa4all_config/secret"
        echo "EPA4All Installer: Created epa4all_config/secret"
    else
        echo "EPA4All Installer: Using existing epa4all_config/secret"
    fi
else
    mkdir -p "epa4all_config/secret"
    echo "EPA4All Installer: Created epa4all_config/secret"
fi

if [ -d "epa4all_config/config/konnektoren/8588" ]; then
    read -p "EPA4All Installer: Directory epa4all_config/config/konnektoren/8588 already exists. Override? (y/n): " override
    if [ "$override" == "y" ]; then
        rm -rf "epa4all_config/config/konnektoren/8588"
        mkdir -p "epa4all_config/config/konnektoren/8588"
        echo "EPA4All Installer: Created epa4all_config/config/konnektoren/8588"
    else
        echo "EPA4All Installer: Using existing epa4all_config/config/konnektoren/8588"
    fi
else
    mkdir -p "epa4all_config/config/konnektoren/8588"
    echo "EPA4All Installer: Created epa4all_config/config/konnektoren/8588"
fi

# STEP 3: Creating application.properties, user.properties and copy p12 file to secret directory

echo
echo "EPA4All Installer: STEP 3: Create application.properties, user.properties and copy p12 file"
echo "EPA4All Installer: Copying p12 file to secret directory ..."
p12_file=$(grep '^konnektor.p12.path=' epa4all.properties | cut -d'=' -f2)
cp "$p12_file" epa4all_config/secret/default_connector.p12
echo "EPA4All Installer: Copied p12 file to secret directory"

# Copy the application.properties entrys from epa4all.properties
head -n 16 epa4all.properties >epa4all_config/config/application.properties
truncate -s -1 epa4all_config/config/application.properties

echo "EPA4All Installer: application.properties:"
echo "-------------------------------  application.properties START  -------------------------------"
cat epa4all_config/config/application.properties
echo
echo "-------------------------------  application.properties END  ---------------------------------"

# Create the user.properties entrys from epa4all.properties
# clientCertificate
if [[ "$(uname)" == "Darwin" ]]; then
    clientCertificate=$(echo -n "data\\:application/x-pkcs12;base64," && cat $p12_file | base64 | sed 's/=/\\=/g')
else
    clientCertificate=$(echo -n "data\\:application/x-pkcs12;base64," && cat $p12_file | base64 -w 0 | sed 's/=/\\=/g')
fi
# clientCertificatePassword
clientCertificatePassword=$(grep '^konnektor.default.cert.auth.store.file.password=' epa4all.properties | cut -d'=' -f2)
# clientSystemId
clientSystemId=$(grep '^konnektor.default.client-system-id=' epa4all.properties | cut -d'=' -f2)
# connectorBaseURL
connectorBaseURL=$(grep '^konnektor.default.url=' epa4all.properties | cut -d'=' -f2 | sed 's/:/\\:/' | cut -d':' -f1,2)
# mandantId
mandantId=$(grep '^konnektor.default.mandant-id=' epa4all.properties | cut -d'=' -f2)
# userId
# version
version=$(grep '^konnektor.default.version=' epa4all.properties | cut -d'=' -f2)
# workplaceId
workplaceId=$(grep '^konnektor.default.workplace-id=' epa4all.properties | cut -d'=' -f2)
# cardlinkServerURL
cardlinkServerURL=$(grep '^cetp.subscriptions.default.cardlink.server.url=' epa4all.properties | cut -d'=' -f2 | sed 's/:/\\:/g')

# Write user.properties
{
    echo "clientCertificate=$clientCertificate"
    echo "clientCertificatePassword=$clientCertificatePassword"
    echo "clientSystemId=$clientSystemId"
    echo "connectorBaseURL=$connectorBaseURL"
    echo "mandantId=$mandantId"
    echo "userId="
    echo "version=$version"
    echo "workplaceId=$workplaceId"
    echo "cardlinkServerURL=$cardlinkServerURL"
} >epa4all_config/config/konnektoren/8588/user.properties
truncate -s -1 epa4all_config/config/konnektoren/8588/user.properties

echo "EPA4All Installer: user.properties:"
echo "-------------------------------  user.properties START  --------------------------------------"
cat epa4all_config/config/konnektoren/8588/user.properties
echo
echo "-------------------------------  user.properties END    --------------------------------------"

# STEP 4: Check if docker is installed and running

echo
echo "EPA4All Installer: STEP 4: Checking Docker installation"

if ! command -v docker &>/dev/null; then
    echo "EPA4All Installer: ERROR: Docker is not installed"
    echo "EPA4All Installer: Please install Docker first and run the installer again"
    exit 1
else
    echo "EPA4All Installer: Docker is installed"
    if ! docker info &>/dev/null; then
        echo "EPA4All Installer: ERROR: Docker daemon is not running"
        echo "EPA4All Installer: Please start Docker daemon and run the installer again"
        exit 1
    fi
    echo "EPA4All Installer: Docker daemon is running"
fi

# Step 5: Check if there is already a running EPA4All container

echo
echo "EPA4All Installer: STEP 5: Checking EPA4All container status"

if docker ps --format '{{.Names}}' | grep -q "^epa4all$"; then
    echo "EPA4All Installer: ERROR: EPA4All container is already running"
    echo "EPA4All Installer: Please stop and remove the container first: docker stop epa4all && docker rm epa4all"
    exit 1
fi

if docker images servicehealtherxgmbh/epa4all -q | grep -q .; then
    read -p "EPA4All Installer: EPA4All image already exists. Pull latest version? (y/n): " pull
    if [ "$pull" == "y" ]; then
        docker pull servicehealtherxgmbh/epa4all
    fi
else
    echo "EPA4All Installer: Pulling EPA4All image..."
    docker pull servicehealtherxgmbh/epa4all
fi

# Step 6: Check if the epa4all-webdav volume exists

echo
echo "EPA4All Installer: STEP 6: Checking EPA4All WebDAV volume"

if docker volume ls -q | grep -q "^epa4all-webdav$"; then
    read -p "EPA4All Installer: Volume epa4all-webdav already exists. Override? (y/n): " override
    if [ "$override" == "y" ]; then
        docker volume rm epa4all-webdav >/dev/null 2>&1
        docker volume create epa4all-webdav >/dev/null 2>&1
        echo "EPA4All Installer: Created new epa4all-webdav volume"
    else
        echo "EPA4All Installer: Using existing epa4all-webdav volume"
    fi
else
    docker volume create epa4all-webdav
    echo "EPA4All Installer: Created epa4all-webdav volume"
fi

# Step 7: Running EPA4All container

echo
echo "EPA4All Installer: STEP 7: Running EPA4All container"

chown -R 1001 epa4all_config/config epa4all_config/secret
quarkus_profile=$(grep '^quarkus.profile=' epa4all.properties | cut -d'=' -f2)
docker run \
    --detach \
    --name epa4all \
    --publish 8090:8090 \
    --publish 8588:8588 \
    --volume "$(pwd)/epa4all_config/secret:/opt/epa4all/secret" \
    --volume "$(pwd)/epa4all_config/config:/opt/epa4all/config" \
    --volume epa4all-webdav:/opt/epa4all/webdav \
    --env QUARKUS_PROFILE=$quarkus_profile \
    servicehealtherxgmbh/epa4all:latest

echo "EPA4All Installer: Container started"
echo
echo "EPA4All Installer: Useful Docker commands:"
echo "EPA4All Installer: ----------------------"
echo "EPA4All Installer: docker stop epa4all           # Stop container"
echo "EPA4All Installer: docker start epa4all          # Start container"
echo "EPA4All Installer: docker rm epa4all             # Remove container"
echo "EPA4All Installer: docker logs epa4all           # View logs"
echo "EPA4All Installer: docker logs -f epa4all        # Follow logs"
echo "EPA4All Installer: docker ps                     # List running containers"
echo "EPA4All Installer: docker ps -a                  # List all containers"
echo "EPA4All Installer: docker restart epa4all        # Restart container"
echo "EPA4All Installer: docker exec -it epa4all bash  # Access shell"
echo
echo "EPA4All Installer: EPA4All is running at: http://localhost:8090"
echo "EPA4All Installer: Installation completed successfully!"
