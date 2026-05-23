import sqlite3, sys
sys.stdout.reconfigure(encoding="utf-8")
c = sqlite3.connect(r"C:\Users\shredder\AppData\Local\Temp\spendy.db")
c.row_factory = sqlite3.Row
print("=== Latest 5 rows by id ===")
for r in c.execute("SELECT id, amountPaise, currency, type, merchantRaw, sender FROM transactions ORDER BY id DESC LIMIT 5"):
    print(f"  id={r['id']} {r['amountPaise']} {r['currency']} {r['type']} merchant='{r['merchantRaw']}' sender='{r['sender']}'")
print(f"\nTotal rows: {c.execute('SELECT COUNT(*) FROM transactions').fetchone()[0]}")
