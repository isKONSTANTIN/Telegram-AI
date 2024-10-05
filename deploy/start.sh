#!/bin/bash

RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

if [ -f ./jvm.env ]; then
  JAVA_OPTS=$(cat ./jvm.env)
fi

if [ -z "$JAVA_OPTS" ]; then
  echo -e "${YELLOW}You can create ./jvm.env file for java options passthrough${NC}"
fi

POSTGRES_PASSWORD=$(cat /dev/urandom | tr -dc '[:alpha:]' | fold -w ${1:-30} | head -n 1)

POSTGRES_PASSWORD=$POSTGRES_PASSWORD JAVA_OPTS=$JAVA_OPTS docker-compose up -d

if [[ $? -eq 0 ]]; then
  echo -e "\nKnst Telegram AI start completed.\nDocker Compose should automatically start containers after system reboot."
else
  echo -e "\n${RED}Start failed.${NC}"
fi
