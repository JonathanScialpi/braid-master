#!/bin/bash
#
# Copyright (C) 2017 Bluebank.
# Author: Salim Badakhchani <salim.badakhchani@bluebank.io>
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program. If not, see <http://www.gnu.org/licenses/>.
#
########################################################################

# Declare command line variables
PROJECT="$1"
IMAGE="$2"
TAG="$3"
DOMAIN="bluebank.io"
REGISTRY="docker-registry-default.${DOMAIN}:443"
VOLUME_NAME="service-scripts"
MOUNT="/home/brain/service-scripts"

# Usage options and user arguments
read -d '' USAGE << EOF
Usage: ./deploy.sh [option] <arg>

Example: ./deploy.sh <openshift project> <docker image> <docker tag> <docker registry>
Working example: ./deploy.sh xxx-nonroot-nginx-dev nonroot-nginx latest docker-registry-default.bluebank.io:443
EOF


ensureVolumeExists() {
  echo
  echo "*** Ensuring volume exists ${VOLUME_NAME} exists and is mounted on ${MOUNT}***"
  echo

  oc volume dc --all --name=${VOLUME_NAME} > /dev/null 2>/dev/null
  if [ $? -eq 0 ]; then
    echo "volume ${VOLUME_NAME} already exists"
  else
    oc volume dc/${IMAGE} --add --name=${VOLUME_NAME} --claim-name=${VOLUME_NAME} --claim-size=1M --mount-path=${MOUNT} --type=persistentVolumeClaim > /dev/null 2>&1
    if [ $? -eq 0 ]; then
    echo "volume ${VOLUME_NAME} created and set to be mounted on ${MOUNT}"
    else
    echo "failed to create ${VOLUME_NAME}"
    fi
  fi
}

ensureProjectExists() {
  echo
  echo "*** Ensuring project $PROJECT exists ***"
  echo

  oc new-project ${PROJECT} > /dev/null 2>/dev/null
  if [ $? -eq 0 ]; then
    echo "project ${PROJECT} created"
  else
    echo "project ${PROJECT} already exists"
  fi
  # switch to project
  oc project ${PROJECT}
}


ensureAppExists() {
  echo
  echo "*** Ensuring app ${IMAGE}:${TAG} exists***"
  echo
  oc describe dc/${IMAGE} > /dev/null 2>/dev/null
  if [ $? -eq 0 ]; then
    echo "deployment config for app ${IMAGE} already exists"
  else
    echo "creating app ${IMAGE}:${TAG}"
    oc new-app ${PROJECT}/${IMAGE}:${TAG}
    if [ $? -eq 0 ]; then
      echo "app ${IMAGE}:${TAG} created"
    else
      echo "app ${IMAGE}:${TAG} already exists"
    fi
  fi
}

createAndPushDockerImage() {
  echo
  echo "*** Creating Docker image ***"
  echo
  docker login --username=$(oc whoami) --password=$(oc whoami -t) ${REGISTRY}
  docker pull ${IMAGE}
  #docker tag ${IMAGE} ${REGISTRY}/${PROJECT}/${IMAGE}:${TAG}
  docker build -t ${REGISTRY}/${PROJECT}/${IMAGE}:${TAG} .
  echo
  echo "*** Pushing Docker image ***"
  echo
  docker push ${REGISTRY}/${PROJECT}/${IMAGE}:${TAG}
}

recreateService() {
  echo
  echo "*** Recreating Service ${IMAGE} ***"
  echo
  oc delete service ${IMAGE} > /dev/null 2>/dev/null
  oc create service nodeport ${IMAGE} --tcp=443:8080
  oc create route edge --hostname=${PROJECT}.${DOMAIN} --service=${IMAGE} --port=8080 --insecure-policy=Redirect
}

# The deploy function checks for the existing project before deploying a clean build from scratch
deploy() {
  ensureProjectExists
  createAndPushDockerImage
  ensureAppExists
  ensureVolumeExists
  recreateService
}

if [[ $# < 1 ]]; then echo "${USAGE}" && exit; fi
while [[ $# > 0 ]]; do OPTS="$1"; shift
done

deploy