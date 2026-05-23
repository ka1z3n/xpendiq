"""Confirm the bill reminder + Wakefit ad rows are gone."""
import sqlite3
import sys

sys.stdout.reconfigure(encoding="utf-8")

DB = r"C:\Users\shredder\AppData\Local\Temp\spendy.db"
conn = sqlite3.connect(DB)
conn.row_factory = sqlite3.Row
cur = conn.cursor()

print("=== Bill reminder check (id=159, amount=2744208, or 'Total Amount Due') ===\n")
for row in cur.execute(
    """
    SELECT id, amountPaise, currency, type, sender, substr(smsBody, 1, 200) AS body
    FROM transactions
    WHERE id = 159
       OR amountPaise IN (2744208, 27442)
       OR smsBody LIKE '%Total Amount Due%'
       OR smsBody LIKE '%27,442%'
    """
):
    print(f"FOUND id={row['id']} amount={row['amountPaise']} {row['currency']} {row['type']}")
    print(f"  sender: {row['sender']}")
    print(f"  body: {row['body']}")
    print()
else:
    pass
print("(empty result above means rows are gone)\n")

print("=== Wakefit ad check (amount near 4263 / Wakefit body) ===\n")
for row in cur.execute(
    """
    SELECT id, amountPaise, currency, type, sender, substr(smsBody, 1, 200) AS body
    FROM transactions
    WHERE amountPaise IN (426300, 4263) OR sender LIKE '%WKEFTT%' OR smsBody LIKE '%Wakefit%'
    """
):
    print(f"FOUND id={row['id']} amount={row['amountPaise']} {row['currency']} {row['type']}")
    print(f"  sender: {row['sender']}")
    print(f"  body: {row['body']}")
    print()

print("\n=== Total transaction count ===")
print(cur.execute("SELECT COUNT(*) FROM transactions").fetchone()[0])
print("\n=== DeletedSmsHash count ===")
print(cur.execute("SELECT COUNT(*) FROM deleted_sms_hashes").fetchone()[0])
