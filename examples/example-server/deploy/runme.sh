#!/bin/bash

# find the true location of this script (with extra bullet proofing)
pushd `dirname $0` > /dev/null
SCRIPTPATH="$( cd "$(dirname "$0")" ; pwd -P )"
popd > /dev/null
echo ${SCRIPTPATH}

pushd "${SCRIPTPATH}/.." # move one level  (this is so that the docker image can be built)
./deploy/deploy.sh braid-sample-server braid-sample latest docker-registry-default.bluebank.io:443
popd # pop back to cwd prior to executing this script
