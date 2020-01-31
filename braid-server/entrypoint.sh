#!/bin/sh

echo "Starting Braid Server process with params"
echo "NODE_RPC_ADDRESS=\"${NODE_RPC_ADDRESS}\""
echo "NODE_RPC_USERNAME=\"${NODE_RPC_USERNAME}\""
echo "NODE_RPC_PASSWORD=\"${NODE_RPC_PASSWORD}\""
echo "PORT=\"${PORT}\""
echo "OPEN_API_VERSION=\"${OPEN_API_VERSION}\""
echo "CORDAPP_DIRECTORY=\"${CORDAPP_DIRECTORY}\""

exec java -jar /opt/braid/braid-server.jar "${NODE_RPC_ADDRESS}" "${NODE_RPC_USERNAME}" "${NODE_RPC_PASSWORD}" "${PORT}" "${OPEN_API_VERSION}" "${CORDAPP_DIRECTORY}"
