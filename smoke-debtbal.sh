#!/usr/bin/env bash
# /moneysmp teambal debtbalance on the dev server: every team with someone in debt pools its
# money and splits it evenly. Starts from an empty economy (the dev data.json is put back
# afterwards). Run from fabric/: bash smoke-debtbal.sh
set -u
cd "$(dirname "$0")"
LOG=run/logs/latest.log
JSON=run/config/moneysmp/data.json
REPORT=run/logs/smoke-debtbal.txt
rm -f "$LOG"
[ -f "$JSON" ] && mv "$JSON" "$JSON.smoke-bak"

feed() {
    while ! grep -q "Done (" "$LOG" 2>/dev/null; do sleep 2; done
    # Red: 100 / -20 / 2 / 10 is $92 between four, $23 each
    echo "moneysmp set Alpha 100"
    echo "moneysmp set Bravo 0"
    echo "moneysmp take Bravo 20"
    echo "moneysmp set Charlie 2"
    echo "moneysmp set Delta 10"
    echo "moneysmp team set Alpha Red"
    echo "moneysmp team set Bravo Red"
    echo "moneysmp team set Charlie Red"
    echo "moneysmp team set Delta Red"
    # Blue: 10 / -50 is underwater, both end up owing $20
    echo "moneysmp set Echo 10"
    echo "moneysmp set Foxtrot 0"
    echo "moneysmp take Foxtrot 50"
    echo "moneysmp team set Echo Blue"
    echo "moneysmp team set Foxtrot Blue"
    # Purple has nobody in debt and must be left alone
    echo "moneysmp set Golf 50"
    echo "moneysmp set Hotel 0"
    echo "moneysmp team set Golf Purple"
    echo "moneysmp team set Hotel Purple"
    echo "moneysmp"
    echo "moneysmp teambal debtbalance"
    echo "moneysmp teambal debtbalance"
    echo "moneysmp teambal"
    echo "moneysmp transaction 1h"
    sleep 3
    echo "stop"
}

feed | ./gradlew.bat runServer --offline --console=plain > /tmp/smoke-debtbal.log 2>&1

{
check() {
    if grep -qF -- "$1" "$LOG"; then echo "PASS  $2"; else echo "FAIL  $2"; fi
}
check "/moneysmp teambal debtbalance" "listed in help"
check "Red evened out its balances to cover \$20 of debt. Everyone now has \$23." "Red pooled and split"
check "Blue evened out its balances to cover \$50 of debt. Everyone now owes \$20." "Blue underwater split"
check "No team debt to spread." "second run finds nothing to do"
check "Team Balances" "plain teambal still lists balances"
check "Team balance evened out across Red" "split shows up in the transaction log"
if grep -qF "Purple evened out" "$LOG"; then echo "FAIL  debt-free Purple was touched"; else echo "PASS  debt-free Purple left alone"; fi
if grep -qE "\[Server thread/ERROR\]|Exception" "$LOG"; then echo "FAIL  errors in log"; grep -E "ERROR|Exception" "$LOG" | head; else echo "PASS  no errors"; fi

python - "$JSON" <<'EOF'
import json, sys
players = {p["name"]: p for p in json.load(open(sys.argv[1]))["players"].values()}
want = {"Alpha": 23, "Bravo": 23, "Charlie": 23, "Delta": 23, "Echo": -20, "Foxtrot": -20, "Golf": 50, "Hotel": 0}
bad = 0
for name, money in want.items():
    got = players[name]["money"]
    ok = abs(got - money) < 1e-6
    bad |= not ok
    print(("PASS" if ok else "FAIL") + f"  {name} saved at {got:g}, expected {money}")
sys.exit(bad)
EOF
} | tee "$REPORT"

rm -f "$JSON"
[ -f "$JSON.smoke-bak" ] && mv "$JSON.smoke-bak" "$JSON"
grep -q FAIL "$REPORT" && exit 1 || exit 0
