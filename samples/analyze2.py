"""Deeper drill: per-issuer message shapes and missed cases."""
import csv
import re
from collections import Counter, defaultdict

CSV_PATH = r"C:\Users\shredder\AndroidStudioProjects\spendy\samples\sms.csv"

def load_rows():
    rows = []
    with open(CSV_PATH, "r", encoding="utf-8", errors="replace") as f:
        for _ in range(3):
            next(f)
        for r in csv.DictReader(f):
            rows.append(r)
    return rows

SENDER_BANK = re.compile(r"^[A-Z]{2}-[A-Z0-9]{4,8}(-[A-Z])?$")

# Keywords
OTP_RE = re.compile(r"\b(otp|one[- ]time password|verification code)\b", re.I)
SCHEDULED_RE = re.compile(r"\b(scheduled on|will be debited|autopay.*scheduled|emi will|will get debited|upcoming|due on)\b", re.I)
BALANCE_REPORT_RE = re.compile(r"\b(reported your (fund|securities) bal|avl(?:bl)? bal|balance is|available balance|current balance|closing bal)\b", re.I)
PROMO_RE = re.compile(r"\b(offer|sale is live|win a|discount|coupon|deal|earn|reward points|emi available|congrats|congratulations|pre[- ]approved|apply now|click here|t&c|claim your|voucher)\b", re.I)

DEBIT_RE = re.compile(r"\b(debited|spent|paid|purchase|withdrawn|w/d|sent|debit of)\b", re.I)
CREDIT_RE = re.compile(r"\b(credited|received|refund|reversal|salary|cashback received|deposited|received on your)\b", re.I)
SIP_MF_RE = re.compile(r"\b(sip purchase|purchase transaction in folio|units allotted|nav of|sip installment|mutual fund purchase|systematic investment)\b", re.I)
CC_PAYMENT_RE = re.compile(r"payment of .*received on your .* credit card", re.I)
CC_SPEND_RE = re.compile(r"(spent|used).*(credit card|debit card).*at ", re.I)

AMOUNT_RE = re.compile(r"(?:rs\.?|inr|₹)\s?([\d,]+\.?\d*)", re.I)
ACC_TAIL_RE = re.compile(r"(?:a/c|account|card)\s*\*?x*(\d{4})", re.I)
UPI_TO_RE = re.compile(r"(?:to|towards)\s+([A-Z0-9 .@_\-/]+?)(?:\s+on|\s+ref|\s+upi|\s+at|$)", re.I | re.M)


def classify(body):
    if OTP_RE.search(body):
        return "OTP"
    if SCHEDULED_RE.search(body):
        return "SCHEDULED"  # do not record
    if BALANCE_REPORT_RE.search(body) and not (DEBIT_RE.search(body) or CREDIT_RE.search(body)):
        return "BALANCE_REPORT"  # informational
    if SIP_MF_RE.search(body):
        return "INVESTMENT"
    if CC_PAYMENT_RE.search(body):
        return "CC_BILL_PAYMENT"  # not income — paying off credit card
    if CC_SPEND_RE.search(body):
        return "CARD_SPEND"
    if DEBIT_RE.search(body) and AMOUNT_RE.search(body):
        return "DEBIT"
    if CREDIT_RE.search(body) and AMOUNT_RE.search(body):
        return "CREDIT"
    if PROMO_RE.search(body):
        return "PROMO"
    if AMOUNT_RE.search(body):
        return "MONEY_OTHER"
    return "INFO"


def main():
    rows = load_rows()
    bank_msgs = [r for r in rows if (r.get("Contact") or "") and SENDER_BANK.match(r.get("Contact") or "") and r.get("Content")]
    print(f"Bank-like messages: {len(bank_msgs)}")

    bucket = Counter()
    samples = defaultdict(list)
    by_sender = defaultdict(Counter)
    for r in bank_msgs:
        body = r["Content"]
        sender = r["Contact"]
        kind = classify(body)
        bucket[kind] += 1
        by_sender[sender][kind] += 1
        if len(samples[kind]) < 12:
            samples[kind].append((sender, body[:400]))

    print("\nBucket counts:")
    for k, v in bucket.most_common():
        print(f"  {k:18s} {v}")

    # Show interesting buckets
    for k in ("SCHEDULED", "CC_BILL_PAYMENT", "CARD_SPEND", "CREDIT", "BALANCE_REPORT", "MONEY_OTHER", "INFO"):
        print(f"\n===== {k} samples =====")
        for sender, body in samples[k][:8]:
            print(f"[{sender}] {body}")
            print("-" * 60)

    # Per-issuer DEBIT shapes — what % of debits have a clear "To <X>" merchant?
    print("\n\n===== HDFC DEBIT shape check =====")
    for r in bank_msgs[:0]:
        pass
    hdfc_dbts = [r for r in bank_msgs if "HDFC" in r["Contact"] and classify(r["Content"]) == "DEBIT"]
    print(f"HDFC debit total: {len(hdfc_dbts)}")
    with_to = sum(1 for r in hdfc_dbts if re.search(r"\bTo\s+\S", r["Content"]))
    print(f"  with explicit 'To <X>': {with_to}")

    # SBI UPI sample
    print("\n===== SBI UPI sample =====")
    for r in bank_msgs:
        if r["Contact"].endswith("SBIUPI") or r["Contact"].endswith("SBIUPI-S"):
            print(f"[{r['Contact']}] {r['Content'][:400]}")
            print("-" * 60)
            if samples.get("_sbiupi_cnt", 0) and samples["_sbiupi_cnt"] > 6:
                break
            samples.setdefault("_sbiupi_cnt", 0)
            samples["_sbiupi_cnt"] += 1

    # SBI Credit Card sample
    print("\n===== SBI Card sample =====")
    cnt = 0
    for r in bank_msgs:
        if "SBICRD" in r["Contact"]:
            print(f"[{r['Contact']}] {r['Content'][:400]}")
            print("-" * 60)
            cnt += 1
            if cnt >= 6:
                break

    # Salary / income
    print("\n===== Possible salary/income =====")
    cnt = 0
    for r in bank_msgs:
        if re.search(r"\b(salary|sal cr|neft|imps|rtgs).*credited|credited.*by (neft|imps|rtgs)|salary credit", r["Content"], re.I):
            print(f"[{r['Contact']}] {r['Content'][:400]}")
            print("-" * 60)
            cnt += 1
            if cnt >= 10:
                break

    # P2P UPI received
    print("\n===== UPI received =====")
    cnt = 0
    for r in bank_msgs:
        if re.search(r"received.*upi|upi.*received|received Rs", r["Content"], re.I) and not OTP_RE.search(r["Content"]):
            print(f"[{r['Contact']}] {r['Content'][:400]}")
            print("-" * 60)
            cnt += 1
            if cnt >= 10:
                break

    # ATM
    print("\n===== ATM =====")
    cnt = 0
    for r in bank_msgs:
        if re.search(r"\batm\b|withdrawn|w/d", r["Content"], re.I):
            print(f"[{r['Contact']}] {r['Content'][:400]}")
            print("-" * 60)
            cnt += 1
            if cnt >= 8:
                break


if __name__ == "__main__":
    main()
