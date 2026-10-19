#!/bin/sh
# Collects evidence for the verification checklist. Run from any machine OUTSIDE the server:
#   sh deploy/verify.sh YOUR-SUBDOMAIN.duckdns.org
set -u
HOST="${1:?usage: verify.sh YOUR-SUBDOMAIN.duckdns.org}"

echo "== DNS =="
nslookup "$HOST" 2>/dev/null | tail -n 3

echo
echo "== Unauthenticated API call (expect 401) =="
curl -s -o /dev/null -w "HTTP %{http_code}\n" "https://$HOST/api/risk-status"

echo
echo "== Invalid token (expect 401) =="
curl -s -o /dev/null -w "HTTP %{http_code}\n" -H "Authorization: Bearer invalid" "https://$HOST/api/risk-status"

echo
echo "== HTTP redirects to HTTPS (expect 301 to https://) =="
curl -s -o /dev/null -w "HTTP %{http_code} -> %{redirect_url}\n" "http://$HOST/"

echo
echo "== Port 8080 must NOT be reachable (expect failure/timeout) =="
curl -s -o /dev/null -m 5 -w "HTTP %{http_code}\n" "http://$HOST:8080/api/risk-status" || echo "not reachable (good)"

echo
echo "== Certificate =="
echo | openssl s_client -connect "$HOST:443" -servername "$HOST" 2>/dev/null \
  | openssl x509 -noout -subject -issuer -dates
curl -sSI "https://$HOST/" >/dev/null && echo "TLS chain verified by curl (valid certificate)"
