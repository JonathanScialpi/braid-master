Bluebank OCP Bring Your Own Container
====================================

## Braid Specific Notes

For `braid-sample-server` you can deploy easily with this command 
```bash
deploy/runme.sh
```

## Quick start

1. Login to OC
Goto [https://openshift.ocp-bluebank.io/console/command-line](https://openshift.ocp-bluebank.io/console/command-line)
Click the copy to clipboard icon for the oc login command paste to the terminal
$ oc login https://openshift.ocp-bluebank.io --token=<hidden>

2. Gitlab / OpenShift integration
Gitlab -> Settings -> Integrations -> Kubernetes ->
`API URL=https://openshift.ocp-bluebank.io`
`Token=<hidden>`
Save Changes

3. Auto DevOps
Gitlab -> Settings -> CI / CD -> General Pipleline Settings (expand) ->
`Enable Auto DevOps=True`
`domain=bluebank.io`
Save Changes




The Bring Your Own Container deployment pattern provides developers with a generic script they can use to build and deploy containers to the Bluebank Openshift Container Platform.

Prerequisites
-------------

The following preresquisites are required before attempting to run this script.

0. A Bluebank user account - contact support@bluebank.io
1. Git - https://git-scm.com/book/en/v2/Getting-Started-Installing-Git
2. Docker - https://docs.docker.com/engine/installation/
3. Openshift client - contact support@bluebank.io

You should confirm you have access to both the Bluebank Source Control Repositories and Openshift Container Platform by browsing to the following URLS.

0. https://gitlab.bluebank.io/
1. https://openshift.ocp-bluebank.io/console/

Before running the script you will need the Openshift access token which you can copy and paste in your terminal from here:

0. https://openshift.ocp-bluebank.io/console/command-line

Instructions
------------

Clone the BYOC git repository to your workstation
```
git clone https://gitlab.bluebank.io/salim.badakhchani/byoc.git
```

Change directory and execute the script
```
cd byoc
./deploy
```

The script will output some usage examples. Simply copy and paste the working example
```
Usage: ./deploy.sh [option] <arg>

Example: ./deploy.sh <openshift project> <docker image> <docker tag> <docker registry>
Working example: ./deploy.sh xxx-nonroot-nginx-dev nonroot-nginx latest docker-registry-default.bluebank.io:443
```

The command line is as follows. Replace the xxx in the project name with your initials. Retain the hyphens!
```
./deploy.sh xxx-nonroot-nginx-dev nonroot-nginx latest docker-registry-default.bluebank.io:443
```

Once the  build and deployment is finished you can browse to your deployment via the Openshift console or by simply 
browsing to `https://xxx-nonroot-nginx-dev.bluebank.io` and remembering to replace the xxx with your the initials you 
used in the previous command invocation.

+1