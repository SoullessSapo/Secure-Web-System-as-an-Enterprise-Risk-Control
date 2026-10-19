#!/bin/sh
# Updates the Duck DNS record with this server's current public IP.
# Install:
#   sudo install -d -m 700 /etc/duckdns
#   echo 'DUCKDNS_TOKEN=your-token' | sudo tee /etc/duckdns/env >/dev/null && sudo chmod 600 /etc/duckdns/env
#   sudo install -m 755 duck.sh /usr/local/bin/duck.sh
#   echo '*/5 * * * * root /usr/local/bin/duck.sh' | sudo tee /etc/cron.d/duckdns
# The token lives only in /etc/duckdns/env (root-only). Never commit it.
set -eu

DUCKDNS_DOMAIN="${DUCKDNS_DOMAIN:-YOUR-SUBDOMAIN}"
. /etc/duckdns/env

result=$(curl -fsS --max-time 15 \
  "https://www.duckdns.org/update?domains=${DUCKDNS_DOMAIN}&token=${DUCKDNS_TOKEN}&ip=")
logger -t duckdns "update ${DUCKDNS_DOMAIN}: ${result}"
[ "$result" = "OK" ]
