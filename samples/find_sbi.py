"""Find SBI Card transactions / SMS in the on-device DB."""
import sqlite3, sys
sys.stdout.reconfigure(encoding="utf-8")
conn = sqlite3.connect(r"C:\Users\shredder\AppData\Local\Temp\xpendiq.db")
conn.row_factory = sqlite3.Row
cur = conn.cursor()

print("=== Rows where body mentions SBI Credit Card ===\n")
for row in cur.execute("""
    SELECT id, amountPaise, currency, type, merchantRaw, sender, occurredAt, smsBody
    FROM transactions
    WHERE smsBody LIKE '%SBI Credit Card%' OR smsBody LIKE '%SBICRD%' OR sender LIKE '%SBICRD%'
    ORDER BY occurredAt DESC
"""):
    print(f"id={row['id']} amount={row['amountPaise']} {row['currency']} {row['type']} merchant={row['merchantRaw']}")
    print(f"  sender: {row['sender']}")
    print(f"  body: {row['smsBody']}")
    print()

print("\n=== Rows containing 23,325 or 2332500 ===\n")
for row in cur.execute("""
    SELECT id, amountPaise, currency, type, merchantRaw, sender, smsBody
    FROM transactions
    WHERE amountPaise = 2332500 OR smsBody LIKE '%23,325%' OR smsBody LIKE '%23325%'
"""):
    print(f"id={row['id']} amount={row['amountPaise']} {row['currency']} {row['type']} merchant={row['merchantRaw']}")
    print(f"  sender: {row['sender']}")
    print(f"  body: {row['smsBody']}")
    print()

print("\n=== Senders matching SBICRD ===\n")
for row in cur.execute("""
    SELECT DISTINCT sender, COUNT(*) AS n FROM transactions
    WHERE sender LIKE '%SBICRD%' OR sender LIKE '%SBIINB%'
    GROUP BY sender
"""):
    print(f"  {row['n']:3d}  {row['sender']}")

print("\n=== Hashes from DeletedSmsHash (truncated) ===\n")
for row in cur.execute("SELECT smsBodyHash FROM deleted_sms_hashes ORDER BY deletedAt DESC LIMIT 20"):
    print(f"  {row['smsBodyHash'][:16]}...")
