"""Sample messages that only hit the generic fallback — to find next extractor candidates."""
import csv, re, sys
from collections import Counter, defaultdict

sys.stdout.reconfigure(encoding="utf-8")
CSV_PATH = r"C:\Users\shredder\AndroidStudioProjects\xpendiq\samples\sms.csv"

SENDER_BANK = re.compile(r"^[A-Z]{2}-[A-Z0-9]{4,8}(-[A-Z])?$")
OTP = re.compile(r"\b(otp|one[- ]time password|verification code|do not share|secure code)\b", re.I)
SCHEDULED = re.compile(r"(scheduled on|will be debited|autopay for[^\n]*scheduled|has requested money|emi will|will get debited|upcoming|due on)", re.I)
BALANCE_REPORT = re.compile(r"(reported your (fund|securities) bal|at eod .* reported your|current balance is|closing bal)", re.I)
PROMO = re.compile(r"(t&c|apply now|claim your|voucher|sale is live|pre[- ]approved|cashback eligible|coupon)", re.I)
TRANS_VERB = re.compile(r"\b(debited|credited|spent|received on|paid|purchase|withdrawn|sent rs|reversal of)\b", re.I)
AMOUNT = re.compile(r"(?:rs\.?|inr|₹)\s?([\d,]+\.?\d*)", re.I)
DEBIT_VERB = re.compile(r"\b(debited|spent|paid|purchase|withdrawn|sent rs)\b", re.I)
CREDIT_VERB = re.compile(r"\b(credited|received|refund|reversal|salary)\b", re.I)

# Now mirror the production extractors INCLUDING the 3 new ones.
EXTRACTORS = [
    ("hdfc_upi_debit",        re.compile(r"Sent\s+Rs\.?\s?[\d,.]+\s+From\s+HDFC Bank\s+A/C\s*\*?\d{4}\s+To\s+.+?\s+On\s+\d{2}/\d{2}/\d{2,4}", re.I | re.S)),
    ("hdfc_credit_alert",     re.compile(r"Rs\.?\s?[\d,.]+\s+credited to\s+HDFC Bank\s+A/c\s+XX\d{4}\s+on\s+\d{2}-\d{2}-\d{2,4}\s+from\s+VPA", re.I | re.S)),
    ("hdfc_deposit",          re.compile(r"INR\s?[\d,.]+\s+deposited\s+in\s+HDFC Bank\s+A/c\s+XX\d{4}", re.I)),
    ("card_spend",            re.compile(r"Rs\.?\s?[\d,.]+\s+spent on your\s+(SBI|HDFC|ICICI|Axis|Kotak)\s+Credit Card\s+ending\s+\d{4}\s+at", re.I)),
    ("icici_card_spend",      re.compile(r"INR\s?[\d,.]+\s+spent using\s+ICICI Bank\s+(?:Credit\s+)?Card\s+XX\d{4}\s+on\s+\d{2}-[A-Za-z]{3}-\d{2,4}\s+on\s+", re.I)),
    ("icici_cc_payment",      re.compile(r"Payment of\s+Rs\s?[\d,.]+\s+has been received on your\s+ICICI Bank Credit Card\s+XX\d{4}", re.I)),
    ("icici_cc_reversal",     re.compile(r"Reversal of\s+Rs\s?[\d,.]+\s+credited to\s+\w+\s+Bank Credit Card\s+XX\d{4}", re.I)),
    ("sbi_upi_debit",         re.compile(r"A/C\s+X+\d{4,6}\s+debited by\s+[\d,.]+\s+on date\s+\d{2}[A-Za-z]{3}\d{2,4}\s+trf to", re.I)),
    ("sbi_nach_debit",        re.compile(r"A/C\s+X+\d{4,6}\s+has a debit by NACH", re.I)),
    ("sbi_nach_credit",       re.compile(r"A/C\s+X+\d{4,6}\s+has a credit by NACH-", re.I)),
    ("mf_sip_hdfc",           re.compile(r"SIP Purchase\s+in Folio", re.I)),
    ("mf_purchase_sbi",       re.compile(r"Purchase transaction in Folio No\.", re.I)),
    ("wallet_debit",          re.compile(r"Payment of\s+Rs\s?[\d,.]+\s+using\s+(Apay|Paytm|Mobikwik)\s+balance is successful", re.I)),
    ("payu_gateway",          re.compile(r"Transaction No\.\s+\d+\s+for\s+Rs\.?\s?[\d,.]+\s+done for", re.I)),
]

def filter_decision(sender, body):
    if not SENDER_BANK.match(sender or ""): return "DROP_NON_BANK"
    if OTP.search(body): return "DROP_OTP"
    if SCHEDULED.search(body): return "DROP_SCHEDULED"
    if PROMO.search(body) and not TRANS_VERB.search(body): return "DROP_PROMO"
    if BALANCE_REPORT.search(body) and not TRANS_VERB.search(body): return "DROP_BALANCE_REPORT"
    return "PARSE"

def specific_match(body):
    for name, rx in EXTRACTORS:
        if rx.search(body): return name
    return None

with open(CSV_PATH, "r", encoding="utf-8", errors="replace") as f:
    for _ in range(3): next(f)
    rows = list(csv.DictReader(f))

# Messages that hit generic fallback only.
generic_groups = Counter()
samples = defaultdict(list)
for r in rows:
    sender = r.get("Contact") or ""
    body = r.get("Content") or ""
    if filter_decision(sender, body) != "PARSE": continue
    if specific_match(body) is not None: continue
    if not AMOUNT.search(body): continue
    if not (DEBIT_VERB.search(body) or CREDIT_VERB.search(body)): continue
    bank = sender.split("-")[1] if "-" in sender else sender
    generic_groups[bank] += 1
    if len(samples[bank]) < 6:
        samples[bank].append((sender, body[:400]))

print(f"Generic-fallback messages: {sum(generic_groups.values())}")
print("\nTop sender groups:")
for bank, cnt in generic_groups.most_common(15):
    print(f"  {cnt:4d}  {bank}")

print("\n===== Samples =====")
for bank, cnt in generic_groups.most_common(10):
    print(f"\n--- {bank} ({cnt}) ---")
    for sender, body in samples[bank][:4]:
        print(f"[{sender}] {body}\n")
