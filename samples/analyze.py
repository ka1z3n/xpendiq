"""Quick exploratory analysis of sms.csv to inform Spendy's parser/category rules."""
import csv
import re
import sys
from collections import Counter, defaultdict

CSV_PATH = r"C:\Users\shredder\AndroidStudioProjects\spendy\samples\sms.csv"

# Skip 3 preamble lines, header on line 4
def load_rows():
    rows = []
    with open(CSV_PATH, "r", encoding="utf-8", errors="replace") as f:
        for _ in range(3):
            next(f)
        reader = csv.DictReader(f)
        for r in reader:
            rows.append(r)
    return rows

SENDER_BANK_PATTERN = re.compile(r"^[A-Z]{2}-[A-Z0-9]{4,8}(-[A-Z])?$")
OTP_RE = re.compile(r"\b(otp|one[- ]time password|verification code)\b", re.I)
PROMO_RE = re.compile(r"\b(offer|cashback eligible|win|discount|sale|coupon|deal|earn|reward points|emi available|congrats|congratulations|loan|pre[- ]approved)\b", re.I)

DEBIT_HINTS = re.compile(r"\b(debited|spent|paid|purchase|withdrawn|w/d|sent|debit|txn of)\b", re.I)
CREDIT_HINTS = re.compile(r"\b(credited|received|refund|reversal|salary|cashback|added to|deposited)\b", re.I)
INVESTMENT_HINTS = re.compile(r"\b(sip|mutual fund|mf purchase|units allotted|nav of|folio|portfolio|nse mfss|cams|kfintech|groww|zerodha|kite|coin|indmoney|smallcase|paytm money|et money)\b", re.I)
AMOUNT_RE = re.compile(r"(?:rs\.?|inr|₹)\s?([\d,]+\.?\d*)", re.I)


def classify(body):
    if OTP_RE.search(body):
        return "OTP"
    if INVESTMENT_HINTS.search(body) and (DEBIT_HINTS.search(body) or "purchase" in body.lower()):
        return "INVESTMENT"
    if CREDIT_HINTS.search(body) and AMOUNT_RE.search(body):
        return "CREDIT"
    if DEBIT_HINTS.search(body) and AMOUNT_RE.search(body):
        return "DEBIT"
    if PROMO_RE.search(body):
        return "PROMO"
    if AMOUNT_RE.search(body):
        return "MONEY_OTHER"
    return "INFO"


def main():
    rows = load_rows()
    print(f"Total rows: {len(rows)}")

    # Sender distribution among non-empty bodies
    senders = Counter()
    for r in rows:
        if r.get("Content"):
            senders[r.get("Contact", "")] += 1

    bank_like = [(s, c) for s, c in senders.items() if SENDER_BANK_PATTERN.match(s or "")]
    bank_like.sort(key=lambda x: -x[1])
    print(f"\nBank-shortcode-like senders: {len(bank_like)} unique, total msgs={sum(c for _,c in bank_like)}")
    for s, c in bank_like[:40]:
        print(f"  {c:5d}  {s}")

    # Classify all bank-like
    bank_like_senders = {s for s, _ in bank_like}
    bucket = Counter()
    samples = defaultdict(list)
    by_sender_buckets = defaultdict(Counter)
    for r in rows:
        body = r.get("Content") or ""
        sender = r.get("Contact") or ""
        if sender not in bank_like_senders:
            continue
        kind = classify(body)
        bucket[kind] += 1
        by_sender_buckets[sender][kind] += 1
        if len(samples[kind]) < 25:
            samples[kind].append((sender, body[:300]))

    print("\nBucket counts (bank-like senders only):")
    for k, v in bucket.most_common():
        print(f"  {k:14s} {v}")

    for k in ("DEBIT", "CREDIT", "INVESTMENT", "MONEY_OTHER", "PROMO", "OTP", "INFO"):
        print(f"\n===== {k} samples =====")
        for sender, body in samples[k][:15]:
            print(f"[{sender}] {body}")
            print("-" * 60)


if __name__ == "__main__":
    main()
