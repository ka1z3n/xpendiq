"""Test the SBI Card regex against real SMS bodies."""
import re
import sys
sys.stdout.reconfigure(encoding="utf-8")

regex = re.compile(
    r"Rs\.?\s?([\d,]+\.?\d*)\s+spent on your\s+(SBI|HDFC|ICICI|Axis|Kotak)\s+Credit Card\s+ending\s+(\d{4})\s+at\s+([A-Z0-9 ./_&-]+?)\s+on\s+(\d{2}/\d{2}/\d{2,4})",
    re.IGNORECASE,
)

bodies = [
    "Rs.12,900.00 spent on your SBI Credit Card ending 9999 at PARCOSAIRPORTHYDERA on 24/01/26. Trxn. not done by you? Report at https://sbicard.com/Dispute",
    "Rs.210.00 spent on your SBI Credit Card ending 9999 at UNISEXSALONMYHOME on 23/01/26.",
    "Rs.34,792.00 spent on your SBI Credit Card ending 9999 at INDIGOAIRLINE on 03/01/26.",
    "Rs 23,325.00 spent on your SBI Credit Card ending 9999 at SOMEMERCHANT on 15/03/26.",
]

for b in bodies:
    m = regex.search(b)
    print(f"BODY: {b[:80]}...")
    print(f"  MATCH: {bool(m)}")
    if m:
        print(f"    groups: {m.groups()}")
    print()
