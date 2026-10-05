#!/usr/bin/env bash
# Team fines and /moneysmp teambal balance on the dev server. Starts from an empty economy
# (the dev data.json is put back afterwards), fines two teams, spreads the debt and checks
# the balances that get saved. Run from fabric/: bash smoke-fine.sh
set -u
cd "$(dirname "$0")"
LOG=run/logs/latest.log
JSON=run/config/moneysmp/data.json
REPORT=run/logs/smoke-fine.txt
rm -f "$LOG"
[ -f "$JSON" ] && mv "$JSON" "$JSON.smoke-bak"

feed() {
    while ! grep -q "Done (" "$LOG" 2>/dev/null; do sleep 2; done
    # Red ends up 80 / -20 / 2 / 10 after the fine. Bravo's $20 debt is a $6.67 share each
    # for the other three: Charlie only has $2, which leaves $9 each for Delta and Alpha
    echo "moneysmp set Alpha 100"
    echo "moneysmp set Bravo 0"
    echo "moneysmp set Charlie 22"
    echo "moneysmp set Delta 30"
    echo "moneysmp team set Alpha Red"
    echo "moneysmp team set Bravo Red"
    echo "moneysmp team set Charlie Red"
    echo "moneysmp team set Delta Red"
    # Blue can't cover its own debt at all, so it just gets evened out
    echo "moneysmp set Echo 10"
    echo "moneysmp set Foxtrot 0"
    echo "moneysmp team set Echo Blue"
    echo "moneysmp team set Foxtrot Blue"
    echo "moneysmp fine Yellow 10 disabled team"
    echo "moneysmp fine Green 10 empty team"
    echo "moneysmp fine Red -5 negative"
    echo "moneysmp fine Red 80 griefing spawn"
    echo "moneysmp fine blue 50 stealing"
    echo "moneysmp teambal"
    echo "moneysmp teambal balance"
    echo "moneysmp teambal balance"
    echo "moneysmp transaction 1h"
    sleep 3
    echo "stop"
}

feed | ./gradlew.bat runServer --offline --console=plain > /tmp/smoke-fine.log 2>&1

{
check() {
    if grep -qF -- "$1" "$LOG"; then echo "PASS  $2"; else echo "FAIL  $2"; fi
}
check "Invalid team Yellow" "disabled team refused"
check "Team Green has no players." "empty team refused"
check "Fine must be above 0." "negative fine refused"
check "Team: Red" "Red fine broadcast"
check "Amount: -\$80 (-\$20 each, 4 members)" "Red fine split four ways"
check "1 member(s) of Red are now in debt." "admin told about Red's debt"
check "Team: Blue" "lowercase team name accepted"
check "Team Balances" "plain teambal still lists balances"
check "Red spread \$20 of debt over the team." "Red debt spread"
check "Blue spread \$40 of debt over the team. (underwater, everyone now owes \$20)" "Blue evened out"
check "No team debt to spread." "second run finds nothing to do"
check "[DEBT_SPLIT]" "spread shows up in the transaction log"
if grep -qE "\[Server thread/ERROR\]|Exception" "$LOG"; then echo "FAIL  errors in log"; grep -E "ERROR|Exception" "$LOG" | head; else echo "PASS  no errors"; fi

python - "$JSON" <<'EOF'
import json, sys
players = {p["name"]: p for p in json.load(open(sys.argv[1]))["players"].values()}
want = {"Alpha": 71, "Bravo": 0, "Charlie": 0, "Delta": 1, "Echo": -20, "Foxtrot": -20}
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
