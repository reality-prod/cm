#!/bin/sh
# SPDX-License-Identifier: AGPL-3.0-or-later
# Computes SPKI SHA-256 pins for every CA in codeberg.org's served chain, plus any extra PEM files you pass
# (download Let's Encrypt's roots/intermediates from https://letsencrypt.org/certificates/ : Root YE, Root YR,
# ISRG Root X1/X2, YE1-3, YR1-3 ...). The LEAF is skipped on purpose: it is re-issued about every 60-90 days.
#
# Usage:  tools/print-pins.sh [host] [extra1.pem extra2.pem ...]
# Needs:  openssl, awk, mktemp.  Verify every printed fingerprint against letsencrypt.org before shipping.
set -eu
host="${1:-codeberg.org}"
[ $# -gt 0 ] && shift
dir="$(mktemp -d)"
trap 'rm -rf "$dir"' EXIT

openssl s_client -connect "$host:443" -servername "$host" -showcerts </dev/null 2>/dev/null |
  awk -v d="$dir" '/-----BEGIN CERTIFICATE-----/{n++; f=sprintf("%s/chain%d.pem", d, n)} n{print > f} /-----END CERTIFICATE-----/{close(f)}'

i=0
for extra in "$@"; do i=$((i + 1)); cp "$extra" "$dir/extra$i.pem"; done

echo "# Leaf (not pinned):"
openssl x509 -in "$dir/chain1.pem" -noout -subject -enddate

pins=""
for f in "$dir"/chain*.pem "$dir"/extra*.pem; do
  [ -f "$f" ] || continue
  case "$f" in */chain1.pem) continue ;; esac
  pin="$(openssl x509 -in "$f" -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | openssl base64)"
  echo "# $(openssl x509 -in "$f" -noout -subject -issuer | tr '\n' ' ')"
  echo "#   sha256 cert: $(openssl x509 -in "$f" -noout -fingerprint -sha256 | cut -d= -f2)"
  echo "#   spki pin:    $pin"
  pins="$pins $pin"
done

echo
echo "<!-- Android: paste inside <domain-config> in network_security_config.xml -->"
echo '<pin-set expiration="2028-01-01">'
for p in $pins; do echo "    <pin digest=\"SHA-256\">$p</pin>"; done
echo '</pin-set>'
echo
echo "# iOS: NSPinnedCAIdentities entries (Info.plist > NSAppTransportSecurity > NSPinnedDomains > codeberg.org)"
for p in $pins; do echo "    SPKI-SHA256-BASE64: $p"; done
