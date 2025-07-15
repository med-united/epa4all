#!/bin/bash
set -euo pipefail

echo '
███████╗██████╗  █████╗ ██╗  ██╗ █████╗ ██╗     ██╗
██╔════╝██╔══██╗██╔══██╗██║  ██║██╔══██╗██║     ██║
█████╗  ██████╔╝███████║███████║███████║██║     ██║
██╔══╝  ██╔═══╝ ██╔══██║╚════██║██╔══██║██║     ██║
███████╗██║     ██║  ██║     ██║██║  ██║███████╗███████╗   by service health erx GmbH
╚══════╝╚═╝     ╚═╝  ╚═╝     ╚═╝╚═╝  ╚═╝╚══════╝╚══════╝   ══════════════════════════'
echo

# Edit this if your epa4all.properties is located somewhere else
CONFIG_FILE="epa4all.properties"

CONFIG_URL="https://raw.githubusercontent.com/med-united/epa4all/main/production_deployment/epa4all.properties"
CONFIG_DIR="epa4all_config"

# Run in SILENT_MODE if you want to skip the user prompts
# Defaults:
# You edited epa4all.properties -> y
# Print epa4all.properties -> n
# Install epa4all with this configuration -> y
# Override CONFIG_DIR -> y
# Print application.properies -> n
# Print user.properties -> n
# Fresh installation -> y
# Override epa4all-webdav directory -> n
SILENT_MODE=false
[[ "${1:-}" =~ ^(-s|--silent)$ ]] && SILENT_MODE=true

# Helper functions

print_step() {
    echo -e "\033[1;34m$1\033[0m"
}

# Prompts user for input with default value in silent mode
# Args: $1 - prompt message, $2 - default value
# Returns: user response or default via stdout
prompt_user() {
    local message="$1"
    local default="$2"
    if [[ "$SILENT_MODE" == "true" ]]; then
        echo "$default"
    else
        read -rp "$message" response
        echo "$response"
    fi
}

# Gets a configuration parameter value from the config file
# Args: $1 - parameter name
# Returns: parameter value via stdout
get_config_param() {
    grep "^$1=" "$CONFIG_FILE" | cut -d'=' -f2
}

# STEP 1: Creating epa4all.properties file
# Pull the default epa4all.properties if it is not present at CONFIG_FILE location

print_step "EPA4All: STEP 1: Configuring EPA4All"
if [[ ! -f "$CONFIG_FILE" ]]; then
    echo "EPA4All: Downloading default epa4all.properties ..."
    curl -o "$CONFIG_FILE" "$CONFIG_URL" || {
        echo "EPA4All: ERROR: Could not download epa4all.properties"
        exit 1
    }
    echo "EPA4All: epa4all.properties downloaded successfully"
    echo "EPA4All: Please edit epa4all.properties and run the installer again"
    exit 0
else
    echo "EPA4All: Found epa4all.properties in current working directory"
    edited_properties=$(prompt_user "EPA4All: Have you already edited epa4all.properties? (y/n): " "y")
    if [[ "$edited_properties" != "y" ]]; then
        echo "EPA4All: Please edit epa4all.properties first and run the installer again"
        exit 0
    fi
fi

print_epa4all_properties=$(prompt_user "EPA4All: Print epa4all.properties? (y/n): " "n")
if [[ "$print_epa4all_properties" == "y" ]]; then
    echo "EPA4All: Current configuration:"
    echo "-----------------------------------  epa4all.properties START  -------------------------------"
    cat "$CONFIG_FILE"
    echo "-----------------------------------  epa4all.properties END    -------------------------------"
fi

install=$(prompt_user "EPA4All: Do you want to install EPA4All with this configuration? (y/n): " "y")
if [[ "$install" != "y" ]]; then
    echo "EPA4All: Installation cancelled"
    exit 0
fi

# STEP 2: Create configuration directory structure
# Create the directory structure which gets mounted to the container. One folder is created for each
# workplace-id in epa4all.properties

echo
print_step "EPA4All: STEP 2: Create configuration directory structure"

workplace_ids_string=$(get_config_param 'workplace-ids')
IFS=',' read -ra workplace_ids <<<"$workplace_ids_string"
create_workplace_config() {
    echo "EPA4All: WorkplaceIDs: $(
        IFS=','
        echo "${workplace_ids[*]}"
    )"

    port_list=""
    port=8588
    for workplace_id in "${workplace_ids[@]}"; do
        port_list+="$port, "
        ((port++))
    done
    port_list="${port_list%, }"
    echo "EPA4All: Ports: $port_list"

    port=8588
    for workplace_id in "${workplace_ids[@]}"; do
        mkdir -p "$CONFIG_DIR/config/konnektoren/$port"
        echo "EPA4All: Created directory $port for '$workplace_id'"
        ((port++))
    done
}

if [[ -d "$CONFIG_DIR" ]]; then
    override=$(prompt_user "EPA4All: Directory ./$CONFIG_DIR already exists. Override? (y/n): " "y")
    if [[ "$override" == "y" ]]; then
        echo "EPA4All: Removing existing ./$CONFIG_DIR"
        rm -rf "$CONFIG_DIR"
        mkdir -p "$CONFIG_DIR"/{secret,config}
        create_workplace_config
        echo "EPA4All: Created directory structure in ./$CONFIG_DIR"
    else
        echo "EPA4All: Using existing ./$CONFIG_DIR"
    fi
else
    mkdir -p "$CONFIG_DIR"/{secret,config}
    create_workplace_config
    echo "EPA4All: Created directory structure in ./$CONFIG_DIR"
fi

# STEP 3: Create application.properties, user.properties and copy konnektor.p12 file to secret directory

echo
print_step "EPA4All: STEP 3: Create application.properties and user.properties"
p12_file=$(get_config_param 'konnektor.p12.path')
if [[ ! -f "$p12_file" ]]; then
    echo "EPA4All: ERROR: Could not find .p12 file at: $p12_file"
    exit 1
fi
if cp "$p12_file" "$CONFIG_DIR/secret/default_connector.p12"; then
    echo "EPA4All: Copied .p12 file to secret directory"
else
    echo "EPA4All: ERROR: Failed to copy .p12 file to secret directory"
    exit 1
fi

# application.properties
# Create the application.properties with configs from epa4all.properties
# If you later want to change more config parameters you can edit the application.properties inside the container
# docker exec -it epa4all bash
# Check default config at https://github.com/med-united/epa4all/blob/main/rest-server/src/main/resources/application.properties
sed -n '/### PARAMS FOR APPLICATION.PROPERTIES START ###/,/### PARAMS FOR APPLICATION.PROPERTIES END ###/p' "$CONFIG_FILE" |
    grep -v '### PARAMS FOR APPLICATION.PROPERTIES' >"$CONFIG_DIR/config/application.properties"

show_application_properties=$(prompt_user "EPA4All: Print application.properties? (y/n): " "n")
if [[ "$show_application_properties" == "y" ]]; then
    echo "EPA4All: application.properties:"
    echo "-------------------------------  application.properties START  -------------------------------"
    cat "$CONFIG_DIR/config/application.properties"
    echo "-------------------------------  application.properties END  ---------------------------------"
fi

# user.properties
# Each workplace needs its own configuration to handle multiple connections
if [[ "$(uname)" == "Darwin" ]]; then
    clientCertificate=$(echo -n "data\\:application/x-pkcs12;base64," && base64 <"$p12_file" | sed 's/=/\\=/g')
else
    clientCertificate=$(echo -n "data\\:application/x-pkcs12;base64," && base64 -w 0 <"$p12_file" | sed 's/=/\\=/g')
fi

clientCertificatePassword=$(get_config_param 'konnektor.default.cert.auth.store.file.password')
clientSystemId=$(get_config_param 'konnektor.default.client-system-id')
connectorBaseURL=$(get_config_param 'konnektor.default.url' | sed 's/:/\\:/' | cut -d':' -f1,2)
mandantId=$(get_config_param 'konnektor.default.mandant-id')
version=$(get_config_param 'konnektor.default.version')

# Create user.properties for each workplace ID
# Base port for workplace connectors (incremented for each workplace)
port=8588
for workplace_id in "${workplace_ids[@]}"; do
    echo "EPA4All: Creating user.properties for workplace ID '$workplace_id' (port $port)"
    cat >"$CONFIG_DIR/config/konnektoren/$port/user.properties" <<EOF
clientCertificate=$clientCertificate
clientCertificatePassword=$clientCertificatePassword
clientSystemId=$clientSystemId
connectorBaseURL=$connectorBaseURL
mandantId=$mandantId
userId=
version=$version
workplaceId=$workplace_id
cardlinkServerURL=wss\://cardlink.service-health.de\:8444/websocket/80276883662000004801-20220128
EOF
    ((port++))
done

show_user_properties=$(prompt_user "EPA4All: Print user.properties files? (y/n): " "n")
if [[ "$show_user_properties" == "y" ]]; then
    port=8588
    for workplace_id in "${workplace_ids[@]}"; do
        echo "EPA4All: user.properties for workplace '$workplace_id' (port $port):"
        echo "-------------------------------  user.properties START  --------------------------------------"
        cat "$CONFIG_DIR/config/konnektoren/$port/user.properties"
        echo "-------------------------------  user.properties END    --------------------------------------"
        ((port++))
    done
fi

# STEP 4: Check if docker is installed and running

echo
print_step "EPA4All: STEP 4: Checking Docker installation"

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
print_step "EPA4All: STEP 5: Checking EPA4All container status"

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

if [[ "$found_containers" = true ]]; then
    echo "EPA4All: Found existing EPA4All setup:"
    echo -e "$containers_status"
    fresh_install=$(prompt_user "EPA4All: Would you like to remove the existing setup and perform a fresh installation? (y/n): " "y")

    if [[ "$fresh_install" == "y" ]]; then
        echo "EPA4All: Removing existing containers ..."
        docker rm -f epa4all epa4all_watchtower >/dev/null 2>&1 || true
        echo "EPA4All: Existing containers removed"
    else
        echo "EPA4All: Installation cancelled"
        exit 1
    fi
fi

docker_tag=$(get_config_param 'docker.tag')
echo "EPA4All: Pulling EPA4All image ..."
docker pull servicehealtherxgmbh/epa4all:"$docker_tag" >/dev/null 2>&1
echo "EPA4All: Finished pulling"

enable_watchtower=$(get_config_param 'enable.watchtower')
if [[ "$enable_watchtower" == "true" ]]; then
    echo "EPA4All: Pulling Watchtower image ..."
    docker pull containrrr/watchtower >/dev/null 2>&1
    echo "EPA4All: Finished pulling"
fi

# Step 6: Check if the epa4all-webdav volume exists

echo
print_step "EPA4All: STEP 6: Checking EPA4All WebDAV volume"

if docker volume ls -q | grep -q "^epa4all-webdav$"; then
    override=$(prompt_user "EPA4All: Volume epa4all-webdav already exists. Override? (y/n): " "n")
    if [[ "$override" == "y" ]]; then
        docker volume rm epa4all-webdav >/dev/null 2>&1 || true
        docker volume create epa4all-webdav >/dev/null 2>&1 || true
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
print_step "EPA4All: STEP 7: Running EPA4All container"

quarkus_profile=$(get_config_param 'quarkus.profile')
mask_sensitive=$(get_config_param 'mask.sensitive')
vsd_test_mode=$(get_config_param 'vsd.test.mode')
if [[ -z "$docker_tag" ]]; then
    echo "EPA4All: ERROR: docker.tag not found in epa4all.properties"
    exit 1
fi
echo "EPA4All: Changing permissions for mounted volumes: chown -R 1001 $CONFIG_DIR"
chown -R 1001 "$CONFIG_DIR"

if [[ "$(uname)" == "Darwin" ]]; then
    chmod -R 777 "$CONFIG_DIR"
fi

# Handle Windows/MSYS path conversion for Docker volume mounting
if [[ "$(uname -s)" == MINGW* ]] || [[ "$(uname -s)" == MSYS* ]]; then
    CONFIG_PATH="$(cygpath -w "$(pwd)/$CONFIG_DIR")"
    SECRET_VOLUME="${CONFIG_PATH}\\secret:/opt/epa4all/secret"
    CONFIG_VOLUME="${CONFIG_PATH}\\config:/opt/epa4all/config"
else
    SECRET_VOLUME="$(pwd)/$CONFIG_DIR/secret:/opt/epa4all/secret"
    CONFIG_VOLUME="$(pwd)/$CONFIG_DIR/config:/opt/epa4all/config"
fi

port_mappings=""
port=8588
for workplace_id in "${workplace_ids[@]}"; do
    port_mappings+="--publish $port:$port "
    ((port++))
done

if docker run \
    --detach \
    --user 1001 \
    --name epa4all \
    --publish 8090:8090 \
    $port_mappings \
    --publish 5005:5005 \
    --publish 3102:3102 \
    --publish 20001:20001 \
    --publish 8787:8787 \
    --volume "$SECRET_VOLUME" \
    --volume "$CONFIG_VOLUME" \
    --volume epa4all-webdav:/opt/epa4all/webdav \
    --env QUARKUS_PROFILE="$quarkus_profile" \
    --env MASK_SENSITIVE="$mask_sensitive" \
    --env VSD_TEST_MODE="$vsd_test_mode" \
    servicehealtherxgmbh/epa4all:"$docker_tag" >/dev/null; then
    echo "EPA4All: EPA4All container started"
else
    echo "EPA4All: Failed to start EPA4All container"
    exit 1
fi

# Update with watchtower only if enabled in epa4all.properties
if [[ "$enable_watchtower" == "true" ]]; then
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
else
    echo "EPA4All: Watchtower disabled"
fi

echo
http_port=$(get_config_param 'quarkus.http.port')
echo "EPA4All: EPA4All is running at: http://localhost:$http_port"
echo "EPA4All: Installation completed successfully!"
