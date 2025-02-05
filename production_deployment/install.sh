#!/bin/bash

echo '
███████╗██████╗  █████╗ ██╗  ██╗ █████╗ ██╗     ██╗     
██╔════╝██╔══██╗██╔══██╗██║  ██║██╔══██╗██║     ██║     
█████╗  ██████╔╝███████║███████║███████║██║     ██║     
██╔══╝  ██╔═══╝ ██╔══██║╚════██║██╔══██║██║     ██║     
███████╗██║     ██║  ██║     ██║██║  ██║███████╗███████╗   by service health erx GmbH
╚══════╝╚═╝     ╚═╝  ╚═╝     ╚═╝╚═╝  ╚═╝╚══════╝╚══════╝   ══════════════════════════'
echo

# STEP 1: Creating the epa4all.properties file

CONFIG_FILE="epa4all.properties"
CONFIG_URL="https://raw.githubusercontent.com/med-united/epa4all/main/production_deployment/epa4all.properties"
PROMTAIL_DEFAULT_CONFIG="https://raw.githubusercontent.com/med-united/epa4all/refs/heads/main/production_deployment/promtail.yaml"

echo "EPA4All: STEP 1: Configuring EPA4All"
if [ ! -f "$CONFIG_FILE" ]; then
    echo "EPA4All: Couldn't find epa4all.properties in current working directory"
    echo "EPA4All: Downloading default epa4all.properties ..."
    curl -o "$CONFIG_FILE" "$CONFIG_URL" || {
        echo "EPA4All: ERROR: Could not download epa4all.properties"
        exit 1
    }
    echo "EPA4All: epa4all.properties downloaded successfully"
    echo "EPA4All: Please edit epa4all.properties first and run the installer again"
    exit 0
else
    echo "EPA4All: Found epa4all.properties in current working directory"
    read -p "EPA4All: Have you already edited epa4all.properties? (y/n): " answer
    if [ "$answer" != "y" ]; then
        echo "EPA4All: Please edit epa4all.properties first and run the installer again"
        exit 0
    fi
fi

echo "EPA4All: Current configuration:"
echo "-----------------------------------  epa4all.properties START  -------------------------------"
cat "$CONFIG_FILE"
echo
echo "-----------------------------------  epa4all.properties END    -------------------------------"

read -p "EPA4All: Do you want to install EPA4All with this configuration? (y/n): " install
if [ "$install" != "y" ]; then
    echo "EPA4All: Installation cancelled"
    exit 0
fi

# STEP 2: Creating the configuration directory structure

echo
echo "EPA4All: STEP 2: Create configuration directory structure"

if [ -d "epa4all_config" ]; then
    read -p "EPA4All: Directory ./epa4all_config already exists. Override? (y/n): " override
    if [ "$override" == "y" ]; then
        echo "EPA4All: Removing existing ./epa4all_config: sudo rm -rf epa4all_config"
        sudo rm -rf "epa4all_config"
        mkdir -p "epa4all_config/secret"
        mkdir -p "epa4all_config/config/konnektoren/8588"
        echo "EPA4All: Created directory structure in ./epa4all_config"
    else
        echo "EPA4All: Using existing ./epa4all_config"
    fi
else
    mkdir -p "epa4all_config/secret"
    mkdir -p "epa4all_config/config/konnektoren/8588"
    echo "EPA4All: Created directory structure in ./epa4all_config"
fi

# STEP 3: Creating application.properties, user.properties and copy p12 file to secret directory

echo
echo "EPA4All: STEP 3: Create application.properties and user.properties"
p12_file=$(grep '^konnektor.p12.path=' epa4all.properties | cut -d'=' -f2)
if [ ! -f "$p12_file" ]; then
    echo "EPA4All: ERROR: Could not find .p12 file at: $p12_file"
    exit 1
fi
if cp "$p12_file" epa4all_config/secret/default_connector.p12; then
    echo "EPA4All: Copied .p12 file to secret directory"
else
    echo "EPA4All: ERROR: Failed to copy .p12 file to secret directory"
    exit 1
fi

# Copy the application.properties entrys from epa4all.properties
sed -n '/### PARAMS FOR APPLICATION.PROPERTIES START ###/,/### PARAMS FOR APPLICATION.PROPERTIES END ###/p' epa4all.properties |
    grep -v '### PARAMS FOR APPLICATION.PROPERTIES' >epa4all_config/config/application.properties
truncate -s -1 epa4all_config/config/application.properties

read -p "EPA4All: Print application.properties? (y/n): " show_properties
if [ "$show_properties" == "y" ]; then
    echo "EPA4All: application.properties:"
    echo "-------------------------------  application.properties START  -------------------------------"
    cat epa4all_config/config/application.properties
    echo
    echo "-------------------------------  application.properties END  ---------------------------------"
fi

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

read -p "EPA4All: Print user.properties? (y/n): " show_user_properties
if [ "$show_user_properties" == "y" ]; then
    echo "EPA4All: user.properties:"
    echo "-------------------------------  user.properties START  --------------------------------------"
    cat epa4all_config/config/konnektoren/8588/user.properties
    echo
    echo "-------------------------------  user.properties END    --------------------------------------"
fi

# Write promtail.yaml
curl -o epa4all_config/config/promtail.yaml "$PROMTAIL_DEFAULT_CONFIG" > /dev/null

grafana_username=$(grep '^grafana.username=' epa4all.properties | cut -d'=' -f2)
grafana_password=$(grep '^grafana.password=' epa4all.properties | cut -d'=' -f2)

sed -i '' \
    -e "s/<GRAFANA_CLOUD_USER_ID>/$grafana_username/g" \
    -e "s/<API_KEY>/$grafana_password/g" \
    epa4all_config/config/promtail.yaml

read -p "EPA4All: Print promtail.yaml? (y/n): " print_promtail_yaml
if [ "$print_promtail_yaml" == "y" ]; then
    echo "EPA4All: promtail.yaml:"
    echo "-------------------------------  promtail.yaml START  --------------------------------------"
    cat epa4all_config/config/promtail.yaml
    echo
    echo "-------------------------------  promtail.yaml END    --------------------------------------"
fi

# STEP 4: Check if docker is installed and running

echo
echo "EPA4All: STEP 4: Checking Docker installation"

if ! command -v docker &>/dev/null; then
    echo "EPA4All: ERROR: Docker is not installed. Please install Docker first and run the installer again"
    exit 1
else
    echo "EPA4All: Docker is installed"
    if ! docker info &>/dev/null; then
        echo "EPA4All: ERROR: Docker daemon is not running. Please start Docker daemon and run the installer again"
        exit 1
    fi
    echo "EPA4All: Docker daemon is running"
fi

# Step 5: Check if there are existing EPA4All containers

echo
echo "EPA4All: STEP 5: Checking EPA4All container status"

found_containers=false
containers_status=""

if docker ps --format '{{.Names}}' | grep -q "^epa4all$"; then
    containers_status+="- epa4all (running)\n"
    found_containers=true
elif docker ps -a --format '{{.Names}}' | grep -q "^epa4all$"; then
    containers_status+="- epa4all (stopped)\n"
    found_containers=true
fi

if docker ps --format '{{.Names}}' | grep -q "^epa4all_watchtower$"; then
    containers_status+="- epa4all_watchtower (running)\n"
    found_containers=true
elif docker ps -a --format '{{.Names}}' | grep -q "^epa4all_watchtower$"; then
    containers_status+="- epa4all_watchtower (stopped)\n"
    found_containers=true
fi

if [ "$found_containers" = true ]; then
    echo "EPA4All: Found existing EPA4All setup:"
    echo -e "$containers_status"
    read -p "EPA4All: Would you like to remove the existing setup and perform a fresh installation? (y/n): " fresh_install

    if [ "$fresh_install" == "y" ]; then
        echo "EPA4All: Removing existing containers ..."
        docker stop epa4all epa4all_watchtower >/dev/null 2>&1
        docker rm epa4all epa4all_watchtower >/dev/null 2>&1
        echo "EPA4All: Existing containers removed"
    else
        echo "EPA4All: Installation cancelled"
        exit 1
    fi
fi

if docker images servicehealtherxgmbh/epa4all -q | grep -q .; then
    read -p "EPA4All: EPA4All image already exists. Pull latest version? (y/n): " pull
    if [ "$pull" == "y" ]; then
        echo "EPA4All: Pulling EPA4All image ..."
        docker pull servicehealtherxgmbh/epa4all >/dev/null 2>&1
        echo "EPA4All: Finished pulling"
    fi
else
    echo "EPA4All: Pulling EPA4All image ..."
    docker pull servicehealtherxgmbh/epa4all >/dev/null 2>&1
    echo "EPA4All: Finished pulling"
fi

if docker images containrrr/watchtower -q | grep -q .; then
    read -p "EPA4All: Watchtower image already exists. Pull latest version? (y/n): " pull_watchtower
    if [ "$pull_watchtower" == "y" ]; then
        echo "EPA4All: Pulling Watchtower image ..."
        docker pull containrrr/watchtower >/dev/null 2>&1
        echo "EPA4All: Finished pulling"
    fi
else
    echo "EPA4All: Pulling Watchtower image ..."
    docker pull containrrr/watchtower >/dev/null 2>&1
    echo "EPA4All: Finished pulling"
fi

# Step 6: Check if the epa4all-webdav volume exists

echo
echo "EPA4All: STEP 6: Checking EPA4All WebDAV volume"

if docker volume ls -q | grep -q "^epa4all-webdav$"; then
    read -p "EPA4All: Volume epa4all-webdav already exists. Override? (y/n): " override
    if [ "$override" == "y" ]; then
        docker volume rm epa4all-webdav >/dev/null 2>&1
        docker volume create epa4all-webdav >/dev/null 2>&1
        echo "EPA4All: Created new epa4all-webdav volume"
    else
        echo "EPA4All: Using existing epa4all-webdav volume"
    fi
else
    docker volume create epa4all-webdav
    echo "EPA4All: Created epa4all-webdav volume"
fi

# Step 7: Running EPA4All container

echo
echo "EPA4All: STEP 7: Running EPA4All container"

quarkus_profile=$(grep '^quarkus.profile=' epa4all.properties | cut -d'=' -f2)
echo "EPA4All: Changing permissions for mounted volumes: sudo chown -R 1001 epa4all_config"
sudo chown -R 1001 epa4all_config
if docker run \
    --detach \
    --user 1001 \
    --name epa4all \
    --publish 8090:8090 \
    --publish 8588:8588 \
    --publish 5005:5005 \
    --publish 3102:3102 \
    --volume "$(pwd)/epa4all_config/secret:/opt/epa4all/secret" \
    --volume "$(pwd)/epa4all_config/config:/opt/epa4all/config" \
    --volume epa4all-webdav:/opt/epa4all/webdav \
    --env QUARKUS_PROFILE=$quarkus_profile \
    servicehealtherxgmbh/epa4all:latest >/dev/null; then
    echo "EPA4All: EPA4All container started"
else
    echo "EPA4All: Failed to start EPA4All container"
    exit 1
fi

if docker run \
    --detach \
    --name epa4all_watchtower \
    --restart unless-stopped \
    --volume /var/run/docker.sock:/var/run/docker.sock \
    containrrr/watchtower \
    --cleanup \
    --interval 60 \
    epa4all >/dev/null; then
    echo "EPA4All: EPA4All Watchtower container started"
else
    echo "EPA4All: Failed to start EPA4All Watchtower container"
    exit 1
fi

echo "EPA4All: docker stop epa4all           # Stop container"
echo "EPA4All: docker start epa4all          # Start container"
echo "EPA4All: docker rm epa4all             # Remove container"
echo "EPA4All: docker logs epa4all           # View logs"
echo "EPA4All: docker logs -f epa4all        # Follow logs"
echo "EPA4All: docker ps                     # List running containers"
echo "EPA4All: docker ps -a                  # List all containers"
echo "EPA4All: docker restart epa4all        # Restart container"
echo "EPA4All: docker exec -it epa4all bash  # Access shell"
echo
echo "EPA4All: EPA4All is running at: http://localhost:8090"
echo "EPA4All: Installation completed successfully!"
