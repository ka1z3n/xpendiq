"""Extract merchant tokens from real debit SMS — for seeding MerchantRule."""
import csv, re, sys
from collections import Counter

sys.stdout.reconfigure(encoding="utf-8")
CSV_PATH = r"C:\Users\shredder\AndroidStudioProjects\xpendiq\samples\sms.csv"
SENDER_BANK = re.compile(r"^[A-Z]{2}-[A-Z0-9]{4,8}(-[A-Z])?$")

with open(CSV_PATH, "r", encoding="utf-8", errors="replace") as f:
    for _ in range(3): next(f)
    rows = list(csv.DictReader(f))

merchants_upi = Counter()
merchants_card = Counter()
vpas_in = Counter()

for r in rows:
    body = r.get("Content") or ""
    sender = r.get("Contact") or ""
    if not SENDER_BANK.match(sender):
        continue

    # HDFC UPI debit: "Sent Rs.X From HDFC Bank A/C *XXXX To <MERCHANT>"
    m = re.search(r"Sent Rs\.[\d,.]+\s*From\s+HDFC Bank[^\n]*\n\s*To\s+([^\n]+)", body, re.I)
    if m:
        merchants_upi[m.group(1).strip().upper()] += 1
        continue

    # Generic "spent ... at <MERCHANT> on dd/mm/yy"
    m = re.search(r"spent.*\s+at\s+([A-Z0-9][A-Z0-9 ./_&-]+?)\s+on\s+\d", body, re.I)
    if m:
        merchants_card[m.group(1).strip().upper()] += 1
        continue

    # HDFC credit: from VPA xxx@yyy
    m = re.search(r"credited.*from\s+VPA\s+([^\s()]+)", body, re.I)
    if m:
        vpas_in[m.group(1).strip().lower()] += 1

print("=== Top UPI debit merchants (HDFC 'To X') ===")
for k, v in merchants_upi.most_common(60):
    print(f"  {v:4d}  {k}")

print("\n=== Top card-spend merchants ('spent at X') ===")
for k, v in merchants_card.most_common(60):
    print(f"  {v:4d}  {k}")

print("\n=== Top incoming VPAs (UPI credit) ===")
for k, v in vpas_in.most_common(30):
    print(f"  {v:4d}  {k}")
