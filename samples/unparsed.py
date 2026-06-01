"""List the messages that pass the production filters but no extractor parses,
grouped by sender suffix and merchant-keyword."""
import csv
import re
import sys
from collections import Counter, defaultdict

sys.stdout.reconfigure(encoding="utf-8")
CSV_PATH = r"C:\Users\shredder\AndroidStudioProjects\xpendiq\samples\sms.csv"

SENDER_BANK = re.compile(r"^[A-Z]{2}-[A-Z0-9]{4,8}(-[A-Z])?$")
OTP = re.compile(r"\b(otp|one[- ]time password|verification code|do not share|secure code)\b", re.I)
SCHEDULED = re.compile(r"(scheduled on|will be debited|autopay for[^\n]*scheduled|has requested money|emi will|will get debited|upcoming|due on)", re.I)
BALANCE_REPORT = re.compile(r"(reported your (fund|securities) bal|at eod .* reported your|current balance is|closing bal)", re.I)
PROMO = re.compile(r"(t&c|apply now|claim your|voucher|sale is live|pre[- ]approved|cashback eligible|coupon)", re.I)
TRANS_VERB = re.compile(r"\b(debited|credited|spent|received on|paid|purchase|withdrawn|sent rs|reversal of)\b", re.I)

# Mirror the production extractors as Python regexes. (Slightly relaxed but same intent.)
EXTRACTORS = [
    ("hdfc_upi_debit",        re.compile(r"Sent\s+Rs\.?\s?[\d,.]+\s+From\s+HDFC Bank\s+A/C\s*\*?\d{4}\s+To\s+.+?\s+On\s+\d{2}/\d{2}/\d{2,4}", re.I | re.S)),
    ("hdfc_credit_alert",     re.compile(r"Rs\.?\s?[\d,.]+\s+credited to\s+HDFC Bank\s+A/c\s+XX\d{4}\s+on\s+\d{2}-\d{2}-\d{2,4}\s+from\s+VPA", re.I | re.S)),
    ("hdfc_deposit",          re.compile(r"INR\s?[\d,.]+\s+deposited\s+in\s+HDFC Bank\s+A/c\s+XX\d{4}", re.I)),
    ("card_spend",            re.compile(r"Rs\.?\s?[\d,.]+\s+spent on your\s+(SBI|HDFC|ICICI|Axis|Kotak)\s+Credit Card\s+ending\s+\d{4}\s+at", re.I)),
    ("icici_cc_payment",      re.compile(r"Payment of\s+Rs\s?[\d,.]+\s+has been received on your\s+ICICI Bank Credit Card\s+XX\d{4}", re.I)),
    ("icici_cc_reversal",     re.compile(r"Reversal of\s+Rs\s?[\d,.]+\s+credited to\s+\w+\s+Bank Credit Card\s+XX\d{4}", re.I)),
    ("sbi_nach_debit",        re.compile(r"A/C\s+X+\d{4,6}\s+has a debit by NACH", re.I)),
    ("sbi_nach_credit",       re.compile(r"A/C\s+X+\d{4,6}\s+has a credit by NACH-", re.I)),
    ("mf_sip_hdfc",           re.compile(r"SIP Purchase\s+in Folio", re.I)),
    ("mf_purchase_sbi",       re.compile(r"Purchase transaction in Folio No\.", re.I)),
    ("wallet_debit",          re.compile(r"Payment of\s+Rs\s?[\d,.]+\s+using\s+(Apay|Paytm|Mobikwik)\s+balance is successful", re.I)),
]

def filter_decision(sender, body):
    if not SENDER_BANK.match(sender or ""):
        return "DROP_NON_BANK"
    if OTP.search(body): return "DROP_OTP"
    if SCHEDULED.search(body): return "DROP_SCHEDULED"
    if PROMO.search(body) and not TRANS_VERB.search(body): return "DROP_PROMO"
    if BALANCE_REPORT.search(body) and not TRANS_VERB.search(body): return "DROP_BALANCE_REPORT"
    return "PARSE"

def parse(sender, body):
    for name, rx in EXTRACTORS:
        if rx.search(body):
            return name
    return None

def main():
    with open(CSV_PATH, "r", encoding="utf-8", errors="replace") as f:
        for _ in range(3): next(f)
        rows = list(csv.DictReader(f))

    unparsed_by_sender_pref = Counter()
    samples = defaultdict(list)
    transactional_hints = Counter()

    for r in rows:
        sender = r.get("Contact") or ""
        body = r.get("Content") or ""
        if filter_decision(sender, body) != "PARSE":
            continue
        if parse(sender, body) is not None:
            continue
        # Unparsed.
        # Sender prefix = the alpha tail after the last hyphen carrier code.
        # Examples: VM-HDFCBK-S -> HDFCBK ; AD-ICICIB -> ICICIB
        parts = sender.split("-")
        bank = parts[1] if len(parts) >= 2 else sender
        unparsed_by_sender_pref[bank] += 1
        if len(samples[bank]) < 8:
            samples[bank].append((sender, body[:400]))
        # Transactional hint counter (does it look like a real txn?)
        if TRANS_VERB.search(body):
            transactional_hints[bank] += 1

    print(f"Total unparsed messages (post-filter): {sum(unparsed_by_sender_pref.values())}")
    print(f"\nTop sender groups (with transactional-verb count):")
    for bank, cnt in unparsed_by_sender_pref.most_common(30):
        print(f"  {cnt:5d}  {bank}  (with txn verb: {transactional_hints[bank]})")

    print("\n\n===== Samples by sender group =====")
    for bank, cnt in unparsed_by_sender_pref.most_common(20):
        print(f"\n--- {bank} ({cnt} unparsed) ---")
        for sender, body in samples[bank][:6]:
            print(f"[{sender}] {body}")
            print()

if __name__ == "__main__":
    main()
