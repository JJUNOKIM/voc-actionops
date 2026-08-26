#!/bin/sh

set -eu

chown -R appuser:appuser /var/lib/voc-actionops

exec gosu appuser "$@"
