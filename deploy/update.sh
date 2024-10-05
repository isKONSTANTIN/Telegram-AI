#!/bin/bash

docker-compose down
git pull
docker-compose pull
./start.sh
