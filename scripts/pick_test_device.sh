#!/usr/bin/env bash
# Print the adb handle of a connected device matched by ro.product.model.
# IP:port and mDNS handles change after reconnect; the model does not.
set -euo pipefail

model="${1:-2206122SC}"
handle=$(adb devices -l | awk -v m="model:${model}" 'index($0, m) { print $1; exit }')

if [ -z "${handle}" ]; then
  echo "no connected device with model ${model}; check 'adb devices -l'" >&2
  exit 1
fi

printf '%s\n' "${handle}"
