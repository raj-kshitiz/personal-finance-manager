#!/usr/bin/env bash
#
# Curl-based smoke test for the Personal Finance Manager API.
#
# Exercises every endpoint and every documented error code (register/login/logout, categories,
# transactions, goals, reports, and the 403-vs-404 / data-isolation rules between two users).
# Intentionally mirrors the shape of the official `financial_manager_tests.sh` test script but
# asserts the exact status code documented in docs/CONTRACT.md rather than a 2xx/4xx range.
#
# Usage:
#   scripts/smoke.sh [BASE_URL]
#
# BASE_URL defaults to http://localhost:8080/api. A fresh, timestamped e-mail address is used for
# every run so the script can be re-run against the same database without hitting 409 Conflict on
# registration. No `jq` dependency — JSON fields are pulled out with grep/sed, same trick the
# official test script uses.

set -u
set -o pipefail

BASE_URL="${1:-http://localhost:8080/api}"
TS=$(date +%s)
NS=$((RANDOM % 100000))

USER1_EMAIL="smoke.user1.${TS}.${NS}@example.com"
USER2_EMAIL="smoke.user2.${TS}.${NS}@example.com"
USER1_PASSWORD="smokePassword123"
USER2_PASSWORD="smokePassword456"

CUSTOM_CAT_INUSE="SmokeInUse_${TS}${NS}"
CUSTOM_CAT_UNUSED="SmokeUnused_${TS}${NS}"
CUSTOM_CAT_DUP="SmokeDup_${TS}${NS}"

WORKDIR=$(mktemp -d)
COOKIE1="$WORKDIR/user1_cookies.txt"
COOKIE2="$WORKDIR/user2_cookies.txt"

cleanup() {
    rm -rf "$WORKDIR"
}
trap cleanup EXIT

TOTAL=0
PASS=0
FAIL=0

STATUS=""
BODY=""

# ---------------------------------------------------------------------------
# helpers
# ---------------------------------------------------------------------------

# req METHOD PATH [DATA] [COOKIE_IN] [COOKIE_OUT]
# Issues the request and populates the globals STATUS and BODY.
req() {
    local method="$1" path="$2" data="${3:-}" cin="${4:-}" cout="${5:-}"
    local args=(-s -m 20 --connect-timeout 10 -w '%{http_code}' -X "$method")
    if [ -n "$data" ]; then
        args+=(-H "Content-Type: application/json" -d "$data")
    fi
    if [ -n "$cin" ]; then
        args+=(-b "$cin")
    fi
    if [ -n "$cout" ]; then
        args+=(-c "$cout")
    fi
    local full
    full=$(curl "${args[@]}" "${BASE_URL}${path}" 2>/dev/null)
    if [ -z "$full" ]; then
        STATUS="000"
        BODY=""
        return
    fi
    STATUS="${full: -3}"
    BODY="${full%???}"
}

# check DESCRIPTION EXPECTED_STATUS
check() {
    local desc="$1" expected="$2"
    TOTAL=$((TOTAL + 1))
    if [ "$STATUS" = "$expected" ]; then
        echo "PASS - $desc [status $STATUS]"
        PASS=$((PASS + 1))
    else
        echo "FAIL - $desc [expected $expected, got $STATUS]"
        echo "       body: $BODY"
        FAIL=$((FAIL + 1))
    fi
}

# json_val BODY KEY -> prints the raw value of the first top-level "KEY": occurrence
json_val() {
    local body="$1" key="$2"
    echo "$body" | grep -o "\"$key\"[[:space:]]*:[[:space:]]*[^,}]*" | head -1 \
        | sed "s/\"$key\"[[:space:]]*:[[:space:]]*//" | tr -d '"'
}

# extract_id BODY -> prints the first numeric "id": value
extract_id() {
    echo "$1" | grep -o '"id"[[:space:]]*:[[:space:]]*[0-9]*' | head -1 | grep -o '[0-9]*'
}

# check_val DESCRIPTION EXPECTED ACTUAL
check_val() {
    local desc="$1" expected="$2" actual="$3"
    TOTAL=$((TOTAL + 1))
    if [ "$actual" = "$expected" ]; then
        echo "PASS - $desc [value=$actual]"
        PASS=$((PASS + 1))
    else
        echo "FAIL - $desc [expected $expected, got $actual]"
        FAIL=$((FAIL + 1))
    fi
}

section() {
    echo ""
    echo "=== $1 ==="
}

echo "Personal Finance Manager smoke test"
echo "Target: $BASE_URL"
echo "Run id: ${TS}${NS}"

# ===========================================================================
# 1. Registration and authentication
# ===========================================================================
section "1. Registration and authentication"

req POST /auth/register "{\"username\":\"$USER1_EMAIL\",\"password\":\"$USER1_PASSWORD\",\"fullName\":\"Smoke One\",\"phoneNumber\":\"+1234567890\"}"
check "register user1" 201

req POST /auth/register "{\"username\":\"$USER2_EMAIL\",\"password\":\"$USER2_PASSWORD\",\"fullName\":\"Smoke Two\",\"phoneNumber\":\"+1987654321\"}"
check "register user2" 201

req POST /auth/register "{\"username\":\"$USER1_EMAIL\",\"password\":\"$USER1_PASSWORD\",\"fullName\":\"Dup\",\"phoneNumber\":\"+1234567890\"}"
check "duplicate registration" 409

req POST /auth/register "{\"username\":\"not-an-email\",\"password\":\"$USER1_PASSWORD\",\"fullName\":\"Bad Email\",\"phoneNumber\":\"+1234567890\"}"
check "register with bad email" 400

req POST /auth/register "{\"username\":\"smoke.badpass.${TS}@example.com\",\"password\":\"short1\",\"fullName\":\"Bad Password\",\"phoneNumber\":\"+1234567890\"}"
check "register with short password" 400

req POST /auth/register "{\"username\":\"smoke.badphone.${TS}@example.com\",\"password\":\"$USER1_PASSWORD\",\"fullName\":\"Bad Phone\",\"phoneNumber\":\"abc123\"}"
check "register with bad phone" 400

req GET /categories "" "" ""
check "protected endpoint without cookie" 401

req POST /auth/login "{\"username\":\"$USER1_EMAIL\",\"password\":\"wrongPassword\"}"
check "login with wrong password" 401

req POST /auth/login "{\"username\":\"$USER1_EMAIL\",\"password\":\"$USER1_PASSWORD\"}" "" "$COOKIE1"
check "login user1" 200

req POST /auth/login "{\"username\":\"$USER2_EMAIL\",\"password\":\"$USER2_PASSWORD\"}" "" "$COOKIE2"
check "login user2" 200

# ===========================================================================
# 2. Categories
# ===========================================================================
section "2. Categories"

req GET /categories "" "$COOKIE1"
check "GET categories" 200
CATEGORIES_BODY="$BODY"

req POST /categories "{\"name\":\"$CUSTOM_CAT_INUSE\",\"type\":\"INCOME\"}" "$COOKIE1"
check "create custom category (to be used)" 201
CAT_INUSE_ID=$(extract_id "$BODY")

req POST /categories "{\"name\":\"$CUSTOM_CAT_UNUSED\",\"type\":\"EXPENSE\"}" "$COOKIE1"
check "create custom category (to stay unused)" 201

req POST /categories "{\"name\":\"$CUSTOM_CAT_INUSE\",\"type\":\"INCOME\"}" "$COOKIE1"
check "duplicate custom category name" 409

req POST /categories "{\"name\":\"Food\",\"type\":\"EXPENSE\"}" "$COOKIE1"
check "category name clashes with default" 409

req POST /categories "{\"name\":\"$CUSTOM_CAT_DUP\",\"type\":\"NOT_A_TYPE\"}" "$COOKIE1"
check "create category with invalid type" 400

req DELETE /categories/Food "" "$COOKIE1"
check "delete default category" 403

req DELETE "/categories/NoSuchCategory_${TS}${NS}" "" "$COOKIE1"
check "delete unknown category" 404

# ===========================================================================
# 3. Transactions
# ===========================================================================
section "3. Transactions"

req POST /transactions "{\"amount\":100.00,\"date\":\"2024-01-15\",\"category\":\"$CUSTOM_CAT_INUSE\",\"description\":\"smoke in-use category\"}" "$COOKIE1"
check "create transaction referencing new category" 201

req DELETE "/categories/$CUSTOM_CAT_INUSE" "" "$COOKIE1"
check "delete category still referenced by a transaction" 400

req DELETE "/categories/$CUSTOM_CAT_UNUSED" "" "$COOKIE1"
check "delete unused custom category" 200

req POST /transactions "{\"amount\":250.50,\"date\":\"2024-01-15\",\"category\":\"Salary\",\"description\":\"smoke txn A\"}" "$COOKIE1"
check "create transaction A (Salary)" 201
TXN_A_ID=$(extract_id "$BODY")

req POST /transactions "{\"amount\":40.00,\"date\":\"2024-01-16\",\"category\":\"Food\",\"description\":\"smoke txn B\"}" "$COOKIE1"
check "create transaction B (Food, kept for isolation test)" 201
TXN_B_ID=$(extract_id "$BODY")

req POST /transactions "{\"amount\":100.00,\"date\":\"2099-01-01\",\"category\":\"Food\",\"description\":\"future\"}" "$COOKIE1"
check "create transaction with future date" 400

req POST /transactions "{\"amount\":-50.00,\"date\":\"2024-01-15\",\"category\":\"Food\",\"description\":\"negative\"}" "$COOKIE1"
check "create transaction with negative amount" 400

req POST /transactions "{\"amount\":0,\"date\":\"2024-01-15\",\"category\":\"Food\",\"description\":\"zero\"}" "$COOKIE1"
check "create transaction with zero amount" 400

req POST /transactions "{\"amount\":100.00,\"date\":\"15-01-2024\",\"category\":\"Food\",\"description\":\"bad date format\"}" "$COOKIE1"
check "create transaction with bad date format" 400

req POST /transactions "{\"amount\":100.00,\"date\":\"2024-01-15\",\"category\":\"NoSuchCategory\",\"description\":\"unknown category\"}" "$COOKIE1"
check "create transaction with unknown category" 400

req GET /transactions "" "$COOKIE1"
check "GET transactions" 200

req GET "/transactions?startDate=2024-01-01&endDate=2024-12-31" "" "$COOKIE1"
check "GET transactions filtered by date range" 200

req GET "/transactions?category=Salary" "" "$COOKIE1"
check "GET transactions filtered by category name" 200

CAT_SALARY_ID=$(extract_id "$CATEGORIES_BODY")
req GET "/transactions?categoryId=$CAT_SALARY_ID" "" "$COOKIE1"
check "GET transactions filtered by categoryId" 200

req GET "/transactions?type=INCOME" "" "$COOKIE1"
check "GET transactions filtered by type" 200

req GET "/transactions?startDate=not-a-date" "" "$COOKIE1"
check "GET transactions with bad date filter" 400

req GET "/transactions?startDate=2024-12-31&endDate=2024-01-01" "" "$COOKIE1"
check "GET transactions with startDate after endDate" 400

req GET "/transactions?categoryId=999999999" "" "$COOKIE1"
check "GET transactions with unknown categoryId" 404

req PUT "/transactions/$TXN_A_ID" "{\"date\":\"2099-12-31\",\"amount\":260.00}" "$COOKIE1"
check "update transaction with date in body (ignored)" 200
NEW_DATE=$(json_val "$BODY" "date")
check_val "updated transaction date is unchanged" "2024-01-15" "$NEW_DATE"

req PUT /transactions/999999999 "{\"amount\":10.00}" "$COOKIE1"
check "update unknown transaction" 404

req PUT /transactions/not-a-number "{\"amount\":10.00}" "$COOKIE1"
check "update transaction with non-numeric id" 400

req DELETE "/transactions/$TXN_A_ID" "" "$COOKIE1"
check "delete transaction" 200

req DELETE "/transactions/$TXN_A_ID" "" "$COOKIE1"
check "delete already-deleted (unknown) transaction" 404

# ===========================================================================
# 4. Savings goals
# ===========================================================================
section "4. Savings goals"

req POST /goals "{\"goalName\":\"Smoke Isolation Goal\",\"targetAmount\":1000.00,\"targetDate\":\"2031-01-01\"}" "$COOKIE1"
check "create goal kept for isolation test" 201
GOAL_ISOLATION_ID=$(extract_id "$BODY")

req POST /goals "{\"goalName\":\"Smoke CRUD Goal\",\"targetAmount\":5000.00,\"targetDate\":\"2030-06-01\",\"startDate\":\"2020-01-01\"}" "$COOKIE1"
check "create goal" 201
GOAL_ID=$(extract_id "$BODY")

req POST /goals "{\"goalName\":\"Past Target\",\"targetAmount\":1000.00,\"targetDate\":\"2020-01-01\"}" "$COOKIE1"
check "create goal with past target date" 400

req POST /goals "{\"goalName\":\"Zero Amount\",\"targetAmount\":0,\"targetDate\":\"2030-01-01\"}" "$COOKIE1"
check "create goal with zero amount" 400

req POST /goals "{\"goalName\":\"Bad Dates\",\"targetAmount\":1000.00,\"targetDate\":\"2025-01-01\",\"startDate\":\"2029-01-01\"}" "$COOKIE1"
check "create goal with startDate after targetDate" 400

req GET /goals "" "$COOKIE1"
check "GET goals" 200

req GET "/goals/$GOAL_ID" "" "$COOKIE1"
check "GET goal by id" 200

req PUT "/goals/$GOAL_ID" "{\"targetAmount\":6000.00}" "$COOKIE1"
check "update goal" 200

req DELETE "/goals/$GOAL_ID" "" "$COOKIE1"
check "delete goal" 200

req DELETE /goals/999999999 "" "$COOKIE1"
check "delete unknown goal" 404

# ===========================================================================
# 5. Reports
# ===========================================================================
section "5. Reports"

req GET /reports/monthly/2024/1 "" "$COOKIE1"
check "GET monthly report" 200

req GET /reports/monthly/2024/0 "" "$COOKIE1"
check "GET monthly report, month 0" 400

req GET /reports/monthly/2024/13 "" "$COOKIE1"
check "GET monthly report, month 13" 400

req GET /reports/yearly/2024 "" "$COOKIE1"
check "GET yearly report" 200

# ===========================================================================
# 6. Data isolation between users
# ===========================================================================
section "6. Data isolation between users"

req GET /categories "" "$COOKIE2"
check "user2 can read categories" 200
if echo "$BODY" | grep -q "$CUSTOM_CAT_INUSE"; then
    TOTAL=$((TOTAL + 1))
    FAIL=$((FAIL + 1))
    echo "FAIL - user2 must not see user1's custom category"
else
    TOTAL=$((TOTAL + 1))
    PASS=$((PASS + 1))
    echo "PASS - user2 cannot see user1's custom category"
fi

req PUT "/transactions/$TXN_B_ID" "{\"amount\":1.00}" "$COOKIE2"
check "user2 cannot update user1's transaction (not found)" 404

req GET "/goals/$GOAL_ISOLATION_ID" "" "$COOKIE2"
check "user2 cannot read user1's goal (forbidden)" 403

req DELETE "/goals/$GOAL_ISOLATION_ID" "" "$COOKIE2"
check "user2 cannot delete user1's goal (forbidden)" 403

# Clean up the goal kept alive for the isolation checks above, as its owner.
req DELETE "/goals/$GOAL_ISOLATION_ID" "" "$COOKIE1"
check "user1 deletes isolation goal (cleanup)" 200

# ===========================================================================
# 7. Logout
# ===========================================================================
section "7. Logout"

req POST /auth/logout "" "$COOKIE1"
check "logout user1" 200

req GET /categories "" "$COOKIE1"
check "reused cookie after logout is rejected" 401

req POST /auth/logout "" "$COOKIE2"
check "logout user2" 200

# ===========================================================================
# Summary
# ===========================================================================
echo ""
echo "==============================================="
echo "Total: $TOTAL  Passed: $PASS  Failed: $FAIL"
echo "==============================================="

if [ "$FAIL" -eq 0 ]; then
    echo "ALL CHECKS PASSED"
    exit 0
else
    echo "SOME CHECKS FAILED"
    exit 1
fi
