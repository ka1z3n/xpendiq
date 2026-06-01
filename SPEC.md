# Xpendiq — Product & Technical Spec

## 1. Overview
Xpendiq is an Android app that tracks personal spending by reading transactional SMS messages sent by banks, card issuers, and UPI apps. Each detected transaction becomes a *spend record*, automatically assigned to a category. Anything Xpendiq can't classify with confidence lands in an **Uncategorized** inbox where the user assigns a category manually.

The app runs fully on-device. No transactional data leaves the phone.

## 2. Goals
- Zero-friction expense tracking — user does not enter spends manually.
- Accurate parsing of Indian bank SMS for UPI, debit card, and credit card transactions.
- Fast, obvious categorization with a clear escape hatch (Uncategorized) for the long tail.
- Useful at-a-glance summaries: this month, this week, by category.

## 3. Non-Goals (v1)
- No cloud sync, no accounts, no login.
- No bill payment.
- **No budgets, no over-spend alerts, no category caps.** Xpendiq reports what happened; it does not judge.
- No OCR of paper receipts, no email parsing.
- No investment portfolio valuation / P&L — Xpendiq only logs that an investment outflow happened.
- INR is the primary currency. USD is supported for foreign-currency subscriptions (rows store the original amount + ISO code), but multi-currency totals are not aggregated yet — non-INR rows are excluded from "Spent / Received / Invested this month".

## 4. Target User
Indian smartphone user whose bank/card issuer sends transactional SMS in English. Primarily uses UPI plus one or two cards.

## 5. Platform & Tech
- **minSdk** 26 (Android 8.0), **targetSdk** 36, **compileSdk** 36.
- Language: Kotlin (current `build.gradle.kts` declares Java 11; add Kotlin Android plugin).
- Architecture: single-module MVVM
  - UI: Activity + Fragments, ViewBinding, Material 3.
  - State: `ViewModel` + `StateFlow`.
  - Persistence: Room (SQLite).
  - Background: `BroadcastReceiver` for live SMS, `WorkManager` for one-time backfill scan.
- No network dependencies in v1.

## 6. Permissions
| Permission | Why | When requested |
|---|---|---|
| `RECEIVE_SMS` | Live parse of incoming bank SMS | Onboarding page 2 |
| `READ_SMS` | One-time backfill scan of last 90 days | Onboarding page 2 (same request) |
| `POST_NOTIFICATIONS` (API 33+) | Notify on new uncategorized spend | Same as above |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Read Google Messages / Samsung Messages notifications to capture **RCS** chatbot messages (e.g. SBI Card now delivers spend SMS via RCS, which never reaches `content://sms/inbox`). | Settings → Notification access; user must toggle in system Settings |

The app must function (read-only browse of already-imported spends) if SMS permissions are later revoked. Notification access is optional — without it, RCS-delivered bank messages are missed; SMS-delivered ones still flow through the broadcast receiver.

## 7. SMS Parsing

### 7.1 Source filtering
Run filters in order; first match wins. Reject anything that matches a filter before going to the parser.

- **Sender shape**: must match `^[A-Z]{2}-[A-Z0-9]{4,8}(-[A-Z])?$` (real SMS use suffixes like `-S` / `-T` / `-P`, e.g. `VM-HDFCBK-S`, `AD-ICICIT-T`, `JD-ICICIT`). Drop everything else (numeric SMS, RBM bot senders like `*_agent@rbm.goog`, ten-digit P2P SMS).
- **Sender suffix `-P`** indicates promotional in DLT taxonomy — treat as soft signal: still scan, but require strong transactional keywords to record.
- **Known-issuer allowlist** (initial): `HDFCBK`, `HDFCBN`, `HDFCMF`, `ICICIB`, `ICICIT`, `ICICIO`, `ICICIS`, `SBIUPI`, `SBICRD`, `SBIMFD`, `SBIMFM`, `CBSSBI`, `AXISBK`, `KOTAKB`, `KOTAKA`, `PYTM`, `PAYTMB`, `BOIIND`. Sender outside the list still parses but is logged so the allowlist can grow.
- **OTP guard**: drop if body contains `OTP`, `one time password`, `verification code`, or a 4–8 digit code followed by `is your` / `do not share`.
- **Scheduled / collect-request guard**: drop if body matches `scheduled on`, `will be debited`, `to be debited from`, `AutoPay for ... scheduled`, `Standing Instructions ... is due`, `has requested money`, `EMI will`, `due on`, `due by <date>`, `upcoming`. These are *notifications about future debits*, not actual transactions.
- **Statement-reminder guard**: drop CC bill / statement reminders. Patterns: `Total (Amount )?Due (of )?Rs`, `Min(imum)? Due Rs`, `Total of Rs X or minimum`, `statement is sent`, `pay total due`, `is due by <date>`, `Delayed/No payments reported to Credit Bureaus`. These show big amounts but represent something the user *needs to* pay, not a transaction that happened.
- **Order-status guard**: drop shipping / order / support updates that happen to contain words like "paid" or "purchase" in marketing context. Patterns: `out for delivery`, `tracking number`, `is confirmed and should be delivered`, `undelivered`, `has been shipped`, `ticket <N> has been (updated|resolved)`, `refund will be processed`.
- **Balance-report guard**: drop if body matches `reported your Fund bal`, `reported your Securities bal`, `EOD ... reported your`, `current balance is`, `closing bal`, **standalone** `Avl Limit ...`, `Available (Credit )?Limit (is|:)` *and* has no transaction verb. Broker EOD messages and isolated CC available-limit pings are not transactions.
- **Promo guard**: drop on `T&C`, `apply now`, `claim your`, `voucher`, `sale is live`, `pre-approved`, `cashback eligible`, `coupon` when there is no strong transaction verb in the body.

### 7.2 Transaction extraction
A `SmsParser` runs an ordered list of issuer-aware extractors. The first one that matches wins. Each extractor returns a `ParsedTransaction { amount, type, paymentMode, merchant?, accountTail?, occurredAt, rawConfidence }`. Real corpora include multi-line bodies — all patterns are `DOTALL` / multiline.

Ordered extractor chain (highest precedence first):

1. **HDFC UPI debit** — multi-line "Sent" alert:
   ```
   Sent Rs.<amt>
   From HDFC Bank A/C *<tail>
   To <MERCHANT>
   On <dd/mm/yy>
   Ref <ref>
   ```
   Regex: `Sent\s+Rs\.?\s?([\d,]+\.?\d*)\s+From\s+HDFC Bank\s+A/C\s*\*?(\d{4})\s+To\s+(.+?)\s+On\s+(\d{2}/\d{2}/\d{2,4})`
   → DEBIT, mode=UPI.

2. **HDFC credit alert** — UPI / NEFT / cheque inflow:
   ```
   Credit Alert!
   Rs.<amt> credited to HDFC Bank A/c XX<tail> on <dd-mm-yy> from VPA <vpa> (UPI <ref>)
   ```
   Also: `Update! INR <amt> deposited in HDFC Bank A/c XX<tail> on <DD-MON-YY> for <CHQ DEP|NEFT-...|IMPS-...>`. → CREDIT, mode by source token.

3. **SBI Credit Card spend**:
   ```
   Rs.<amt> spent on your SBI Credit Card ending <tail> at <MERCHANT> on <dd/mm/yy>
   ```
   Regex: `Rs\.?\s?([\d,]+\.?\d*)\s+spent on your\s+(SBI|HDFC|ICICI|Axis)\s+Credit Card\s+ending\s+(\d{4})\s+at\s+([A-Z0-9 ./_&-]+?)\s+on\s+(\d{2}/\d{2}/\d{2,4})` → DEBIT, mode=CARD_CREDIT. Note merchant tokens often arrive with spaces stripped (`NEERATICULINARYCANVAS`) — normalize via `merchantNormalized = upper().replace(' ', '')` for rule matching.

4. **ICICI CC payment received** — credit-card bill payment, NOT income:
   ```
   Payment of Rs <amt> has been received on your ICICI Bank Credit Card XX<tail> through Bharat Bill Payment System on <dd-MON-yy>
   ```
   → CREDIT, mode=CARD_CREDIT, category auto-assigned to **Transfers (CC payment)**, kept out of "income" totals.

5. **ICICI CC reversal**:
   ```
   Reversal of Rs <amt> credited to ICICI Bank Credit Card XX<tail> on <dd-MON-yy>
   ```
   → CREDIT, mode=CARD_CREDIT, category **Refund**.

6. **SBI account NACH debit/credit** — auto-debit (SIP/EMI/utility) or NACH credit (dividend/refund):
   ```
   Dear Customer, Your A/C XXXXX<tail> has a debit by NACH of Rs <amt> on <dd/mm/yy>. Avl Bal Rs <bal>
   Dear Customer, Your A/C XXXXX<tail> has a credit by NACH- <PAYER> of Rs <amt> on <dd/mm/yy>
   ```
   → DEBIT/CREDIT, mode=AUTO_DEBIT for debits, mode=NETBANKING for credits. PAYER captured as merchant.

7. **Mutual fund / SIP purchase** — INVESTMENT:
   ```
   Your SIP Purchase in Folio <folio> under <SCHEME> for Rs. <amt> has been processed at the NAV of <nav> for <units> units and <date>
   Dear Investor, Purchase transaction in Folio No. <folio> in Scheme : <SCHEME> for date <date> for amount of INR <amt> at NAV of <nav>...
   ```
   → INVESTMENT. Scheme name captured as merchant. `paymentMode = AUTO_DEBIT` (SIP).

8. **ICICI card spend** — Indian credit-card spend with INR or USD amount:
   ```
   INR <amt> spent using ICICI Bank Card XX<tail> on <dd-MON-yy> on <MERCHANT>. Avl Limit: INR <bal>.
   ```
   Also accepts `USD <amt> spent using ICICI Bank Card XX...` for foreign-currency subscriptions. → DEBIT, mode=CARD_CREDIT, currency captured from the amount prefix.

9. **SBI UPI debit**:
   ```
   Dear UPI user A/C X<tail> debited by <amt> on date <DDMMMYY> trf to <MERCHANT> Refno <ref>
   ```
   → DEBIT, mode=UPI. Bare-rupee amount (no `Rs.` prefix); date in compact `06Aug25` form.

10. **SBI NEFT credit** — salary / large incoming transfer:
    ```
    Dear Customer, INR <amt> credited to your A/c No XX<tail> on dd/mm/yyyy through NEFT with UTR <utr> by <PAYER>, INFO: ... SALARY
    ```
    → CREDIT, mode=NETBANKING. If body contains `SALARY` keyword, merchant marker is `__SALARY_NEFT__` and Categorizer auto-routes to **Income**.

11. **PayU gateway**:
    ```
    Transaction No. <id> for Rs. <amt> done for <MERCHANT> has succeeded Team PayU
    ```
    → DEBIT, mode=UPI.

12. **FASTag toll** (ICICI Amazon Pay FASTag + IndusInd FASTag):
    ```
    Rs.<amt> paid at <LOCATION> for <VEHICLE> on <date> with Amazon Pay ICICI Bank FASTag
    Rs.<amt> toll paid at <LOCATION> on <date> for <VEHICLE> via FASTag
    ```
    → DEBIT, mode=AUTO_DEBIT, merchant marker `__FASTAG_TOLL__`, Categorizer auto-routes to **Transport**.

13. **Apay / wallet debit** — Amazon Pay balance, Paytm wallet, etc.:
    ```
    Payment of Rs <amt> using Apay balance is successful at <MERCHANT>. Updated balance is Rs <bal>
    ```
    → DEBIT, mode=UPI (treat wallet as UPI for v1).

14. **Generic fallback** — tightened to avoid false positives from marketing/shipping SMS. Requires **all three**:
    - amount with currency prefix (`Rs` / `INR` / `₹` / `USD` / `$`),
    - one of the **strong** debit verbs (`debited`, `spent`, `withdrawn`, `sent rs`) — `paid` and `purchase` were dropped because they appear in promo bodies, OR a strong credit verb (`credited`, `salary credited`, `reversal of`, `refund of`),
    - a bank/payment context token (`A/c`, `account`, `card`, `UPI`, `NEFT`, `IMPS`, `RTGS`, `Transaction`, `Txn`).

    Becomes a LOW-confidence row with no merchant → Uncategorized.

Implementation note: each extractor is a small class with `match(sender, body, receivedAtMillis) -> ParsedTransaction?` and returns its own `currency` (default `"INR"`). The chain is unit-tested against `samples/sms.csv` (see §7.5).

### 7.3 Confidence
- **High**: amount + merchant + transaction type all extracted by a specific (non-fallback) extractor → auto-saved with detected category if available.
- **Low**: amount only, or merchant missing, or matched by the generic fallback → saved into Uncategorized regardless of merchant-keyword match.

### 7.4 Cross-type invariants
- CREDIT records **never** modify an existing DEBIT. Refunds, reversals, salary, P2P inflows, and CC bill payments are all standalone rows on the Credits list.
- INVESTMENT records are kept out of spend totals and credit totals — they have their own page.
- A "Payment received on your Credit Card" is a CREDIT to the card (you paying off the bill from your bank), categorized as **Transfers (CC payment)** and **hidden** from the Credits list and "Received this month" totals (it's an internal transfer, not income).
- **Cross-type re-categorization is allowed**: from the Uncategorized inbox, the user can move a DEBIT row into the Investment category. The destination category's `appliesToType` becomes the row's new `type`. The Repository's `ingestSms` honours this too — if a learned `MerchantRule` points at a category of a different type, the inserted row gets retyped automatically.

### 7.5 Multi-currency (USD)
- Each transaction stores an ISO-4217 `currency` code on the Room row (default `"INR"`).
- `ParseUtil.findAmountWithCurrency` recognises `Rs`, `INR`, `₹`, `USD`, `$` and returns both amount-in-minor-units and the currency code.
- All extractors carry currency through. `CurrencyFormat.format(paise, currency)` renders the right symbol (₹ or $) on rows, the detail sheet, and the Edit screen's amount prefix.
- Monthly totals (Home / Insights) query `WHERE currency = 'INR'` — USD rows are visible but never aggregated into the INR totals, because we have no FX rates and don't want misleading sums.

### 7.6 RCS support via NotificationListener
Some banks (notably **SBI Card**) deliver "Rs.X spent..." messages as **RCS chatbot** messages on Google Messages, not as SMS. RCS messages don't reach the `SMS_RECEIVED` broadcast or `content://sms/inbox`. Google Messages stores them in its own private content provider that third-party apps can't query.

Workaround: a `NotificationListenerService` registered against the Google Messages and Samsung Messages packages. When the user grants Notification access (Settings → Notification access → Xpendiq), every incoming notification from those apps is read, the title (sender) and `BIG_TEXT` / `MessagingStyle` body are extracted, and the same `Repository.ingestSms` pipeline runs with `bypassSenderCheck = true` (notification titles are friendly names like `SBI Card`, not shortcodes like `VM-SBICRD-S`, so the bank-shortcode filter is skipped).

Dedup: the content-based hash `sha256(sender + body.trim())` (see §9) means an SMS arriving via both the broadcast receiver and the notification listener is stored only once.

Caveats:
- Only **new** notifications are visible. Existing RCS messages in Google Messages from before the user granted access are unreachable.
- Notification text can be truncated; we prefer `EXTRA_BIG_TEXT` / `MessagingStyle.messages.last().text` over `EXTRA_TEXT`.
- The `Notification access` permission is privileged; OEM battery savers may stop the listener. If the user reports missing rows, ask them to whitelist Xpendiq from background restrictions.

### 7.7 Test corpus
`samples/sms.csv` is the source-of-truth corpus (real anonymized SMS, ~7.4k bank-shortcode messages). Parser unit tests assert:
- ≥ 90% of transactional SMS produce a `ParsedTransaction`.
- 100% of SMS matching the filter guards (OTP, scheduled, balance-report, promo) produce nothing.
- Zero CC-bill-payment messages are mis-classified as Income.
- Zero "AutoPay scheduled" / "has requested money" messages produce a transaction.
- Sample messages observed in the corpus:
  - HDFC UPI debit: `Sent Rs.60.00 / From HDFC Bank A/C *1234 / To Ramesh Kumar / On 11/01/26 / Ref 200000000000`
  - HDFC credit: `Credit Alert! Rs.60000.00 credited to HDFC Bank A/c XX1234 on 19-11-25 from VPA john.doe@oksbi`
  - SBI Card spend: `Rs.1,268.50 spent on your SBI Credit Card ending 9999 at ATRIACONVERGENCETECH on 05/12/25`
  - ICICI CC payment: `Payment of Rs 16,002.17 has been received on your ICICI Bank Credit Card XX5151 through Bharat Bill Payment System on 23-MAY-25`
  - SIP: `Your SIP Purchase in Folio 12345678/90 under HDFC BSE Sensex Index Fund-DP Growth for Rs. 19,999.00 has been processed at the NAV of 774.657`
  - Scheduled (ignore): `Dear UPI User, UPI AutoPay for AutoPay Bharat Connect Electricity Bill Payment debit of Rs.611.00 is scheduled on .05/02/26`
  - Collect-request (ignore): `MakeMyTrip has requested money through Google-pay. On approval, Rs 3450.00 will be debited from your Bank Account-ICICI Bank.`
  - Balance report (ignore): `GROWW INVEST at EOD 27/02/2026 reported your Fund bal Rs 378.900 & Securities bal 0`

## 8. Categorization

### 8.1 Default categories (seeded on first launch)

Categories are scoped by transaction `type` — a Spend category list, a Credit category list, and Investments as its own bucket. The same name can repeat across types (e.g. there's a Spend "Transfers" and a Credit "Transfers").

**Spend categories** (`type = DEBIT`):
- Food & Dining
- Groceries
- Transport (fuel, cabs, metro, FASTag tolls)
- Shopping
- **Subscriptions** (Netflix, Spotify, Cursor, Claude, Google Play, etc.)
- Bills & Utilities
- Entertainment (one-off — BookMyShow, PVR, INOX)
- Health
- Travel
- Rent
- Transfers (P2P UPI out)
- Other
- **Uncategorized** (system, not user-deletable)

**Credit categories** (`type = CREDIT`):
- Refund
- Transfers (P2P UPI in)
- Transfers (CC payment)  — auto-assigned for "Payment received on Credit Card" SMS; not counted as income
- Income (salary, payouts)
- Cashback / Rewards
- Interest
- Cheque / NEFT deposit
- Other
- **Uncategorized** (system, not user-deletable)

**Investment** (`type = INVESTMENT`): a single bucket in v1 — no sub-categories. Surfaced on its own page, not in spend totals.

Each category has: `id`, `name`, `iconKey`, `colorHex`, `isSystem`, `appliesToType`.

### 8.2 Auto-categorization
A `Categorizer` maps a `ParsedTransaction` to a category via a `MerchantRule` table:
```
MerchantRule { id, pattern (regex/substring), categoryId, appliesToType, priority, source = SEED | USER_LEARNED }
```
- Rules are scoped per parse-time `type`, so a merchant can resolve differently for a debit vs. a credit.
- Merchant matching is done on `merchantNormalized` (uppercase, spaces stripped) so that `VANLAVINO CAFE` (UPI) and `VANLAVINOCAFE` (SBI Card, no spaces) both match the same rule.
- **Priority semantics**: rules are sorted ASC and the first match wins. `USER_LEARNED` rules get priority **50**, seeded rules **100**, and merchant-specific rules that need to beat a broader seeded rule (e.g. `INSTAMART` must beat `SWIGGY`) also use **50**. Lower number wins.
- **Cross-type rules**: a `MerchantRule.appliesToType` is the *parse-time* type to match; the resolved `Category.appliesToType` is the *destination* type. The Repository uses `category.appliesToType` as the row's final `type` on insert, so a learned rule pointing at the Investment category retypes a parsed DEBIT into an INVESTMENT row.
- Seeded **DEBIT** rules (substring on `merchantNormalized`, derived from the real corpus):
  - **Food & Dining**: `SWIGGY`, `ZOMATO`, `VANLAVINOCAFE`, `BELLAMCHAI`, `MYTIFOODWORKS`, `HARLEYSFINEBAKING`, `NEERATICULINARYCANVAS`, `UDIPISUPAHAR`, `BHARATIYAMFOOD`, `KIREETIAAHAR`, `STARBUCKS`, `HOTELRAGHAVENDRA`, `TRAVELFOODSERVICES`, `CAMPAHYD`
  - **Groceries** (priority 50 for the SWIGGY-overlap ones so they beat Food): `INSTAMART`, `SWIGGYINSTAMAR`, `BUNDL`, `DELIGHTFUL`, `DELIGHTFULGOUR`, `RAMAREDDY`. Plus seeded at priority 100: `BLINKIT`, `ZEPTO`, `BIGBASKET`, `RATNADEEP`, `STARBAZAAR`, `DUNZO`, `LICIOUS`, `FRESHTOHOME`, `COUNTRYDELIGHT`, `MILKBASKET`
  - **Transport**: `UBER`, `OLA`, `RAPIDO`, `IRCTC`, `IRCTCETICKETING`, `IRCTCRAILWEB`, `HPPAYDIRECT`, `PAKHIFUELSLLP`, `RELAYSHYD`, `T1POPNGO`. FASTag tolls (any location) auto-route here via the `__FASTAG_TOLL__` marker.
  - **Bills & Utilities**: `ATRIACONVERGENCE`, `JIORECHARGE`, `JIO`, `AIRTEL`, `BBPS`, `LIVPURE`, `RAILTEL`
  - **Health**: `APOLLOPHARMACY`, `MEDICCARE`, `KCMPHARMACY`, `STARHOSPITALS`, `VASANEYECARE`
  - **Shopping**: `MYNTRA`, `AJIO`, `FLIPKART`, `AMAZON`, `CROMA`, `LIFESTYLE`, `MAXFASHION`
  - **Travel**: `MAKEMYTRIP`, `MMTRIP`, `INDIGO`, `INDIGOAIRLINE`, `AIRINDIA`, `CLEARTRIP`, `GOIBIBO`, `OYO`
  - **Entertainment** (one-off only): `BOOKMYSHOW`, `PVR`, `INOX`, `NOVDIGITALENTERTAINMENT`
  - **Subscriptions** (priority 50 to beat any overlap with Shopping/Entertainment): `NETFLIX`, `SPOTIFY`, `PRIMEVIDEO`, `AMAZONPRIME`, `HOTSTAR`, `DISNEY`, `DISNEYHOTSTAR`, `CURSOR`, `CLAUDE`, `ANTHROPIC`, `OPENAI`, `CHATGPT`, `CRUNCHYROLL`, `GOOGLEPLAY`, `GPLAY`, `GOOGLE`, `APPLE`, `APPLEONE`, `APPLEMUSIC`, `APPLETV`, `ITUNES`, `YTPREMIUM`, `YOUTUBE`, `NOTION`, `FIGMA`, `DROPBOX`, `MEDIUM`, `SUBSTACK`, `GITHUB`, `LINKEDIN`
  - **Rent**: *(no seeded rules — rent payees vary too widely; user assigns from Uncategorized)*
  - **No personal-name heuristic.** Bare personal-name strings (`PRAKASH`, `RANGAPUR SHANKAR`, `MS MULE SRIVYSHNAV`) are *not* defaulted to Transfers — in the corpus the majority are local vendors (chai stalls, sabzi-walas, kirana shops). Anything without a merchant-rule hit goes to Uncategorized.
- Seeded **CREDIT** routes: `__CC_BILL_PAYMENT__` marker → Transfers (CC payment); `__CARD_REVERSAL__` marker → Refund; `__SALARY_NEFT__` marker → Income.
- Seeded **INVESTMENT** rules: matched at parse time by the SIP extractor; no merchant rule needed in v1 since there's only one Investment bucket.
- When the user manually categorizes an Uncategorized entry, Xpendiq offers: *"Always categorize <type>s from &lt;merchant&gt; as &lt;category&gt;?"* — accepting writes a `MerchantRule` with `source = USER_LEARNED, priority = 50` scoped to the original parse-time type. Cross-type picks also flip the type on the existing matching rows in a bulk `UPDATE`.
- If no rule matches, the entry is placed in Uncategorized for its parse-time type.

### 8.3 User categories
Users can add, rename, recolor, and delete their own categories. Deleting a non-empty category prompts: reassign existing spends to *Other* or *Uncategorized*.

## 9. Data Model (Room)
```
Transaction                  // single table — DEBIT, CREDIT, INVESTMENT (Room entity: TransactionEntity)
  id: Long (PK)
  amountPaise: Long          // store in minor units (paise / cents) to avoid float
  currency: String           // ISO-4217; defaults "INR". USD also supported.
  type: Enum { DEBIT, CREDIT, INVESTMENT }
  paymentMode: Enum { UPI, CARD_CREDIT, CARD_DEBIT, NETBANKING, AUTO_DEBIT, UNKNOWN }
  merchantRaw: String?
  merchantNormalized: String?
  accountTail: String?       // last 4 digits, nullable
  categoryId: Long           // FK; points to the type-appropriate Uncategorized when unknown
  occurredAt: Long           // epoch millis — date AND time, sourced from SMS body when present, else SMS receive time
  smsId: Long?               // original SMS _id for dedupe (null for manual rows + notification-sourced)
  smsBodyHash: String        // sha256(sender + "|" + body.trim()) — dedupe key. Content-only; parser-independent so re-parses don't dup.
                             //                                       Manual rows use a synthetic "manual:$UUID" sentinel.
  smsBody: String?           // null for manual rows
  sender: String?            // null for manual rows
  notes: String?             // user-editable
  isUserEdited: Boolean      // true if user changed any parsed field, OR for manual rows
  createdAt: Long
  updatedAt: Long

Category
  id, name, iconKey, colorHex, isSystem, sortOrder, appliesToType   // DEBIT | CREDIT | INVESTMENT

MerchantRule
  id, pattern, categoryId, appliesToType, priority, source           // source = SEED | USER_LEARNED

DeletedSmsHash
  smsBodyHash (PK), deletedAt                                        // prevents backfill from resurrecting deleted rows

IgnoredSender
  senderId, addedAt                                                   // user-added blocks (e.g. promotional senders); DAO exists, no UI yet
```
Indexes on `Transaction.occurredAt`, `Transaction.categoryId`, `Transaction.type`, unique on `Transaction.smsBodyHash`. The `currency` column was added in DB v2 with an `ALTER TABLE ... DEFAULT 'INR'` migration; the rest were created at DB v1.

## 10. Screens

Bottom nav: 5 destinations, **icon-only** (no labels) so it stays slim. Custom Material-style vector icons for Home, Transactions, Investments, Insights, Settings. The BottomNavigationView's bottom padding picks up the system gesture inset so its background extends to the screen edge.

1. **Onboarding** — 2-page ViewPager2 flow shown only on first launch (gated by a `Preferences.onboarding_done` flag). Page 1: what Xpendiq does + 4 bullet points. Page 2: permission rationale + **Allow SMS access** + **Skip for now**. After grant: dialog "Run a 90-day backfill now?" → enqueues `BackfillWorker`. Then proceeds to MainActivity.
2. **Home / This Month** — permission banner (when missing), Uncategorized review card (badge + count, navigates to Uncategorized Inbox), **Spent this month** big total, two small cards (Received / Invested), **Top categories** card (top 3 with coloured dot + ₹ amount + % of those 3) with "See all" → Insights, **Recent transactions** card (last 5 DEBITs, tap → detail sheet) with "See all" → Transactions tab.
3. **Transactions** — TabLayout: **Spends | Credits**. Date-grouped list with tinted category chips (category colour at ~20% alpha background, full-saturation text). Tap row → detail bottom sheet. **+ Add transaction** FAB in bottom-right opens the Edit screen in Add mode. CC bill payments are hidden from the Credits tab.
4. **Uncategorized Inbox** — Tabs: Spends / Credits. Tap a row → category picker bottom-sheet showing **all** categories grouped under "Spends", "Credits", "Investments" section headers (cross-type moves allowed). Toggle: "Always categorize from &lt;merchant&gt; this way" — writes a `USER_LEARNED MerchantRule` (priority 50) and bulk-updates existing matching rows. **Delete** button in the sheet header for rows the user wants removed (e.g. self-transfers).
5. **Transaction Detail** — bottom sheet showing amount, merchant, category, payment mode, date/time, account tail (if any), original SMS body (read-only), notes. Actions: **Edit**, **Delete** (confirm; smsBodyHash recorded in DeletedSmsHash).
6. **Add / Edit Transaction** — same Fragment, `Mode.Add` vs `Mode.Edit(txnId)` selected by the `txnId` nav arg (`-1L` → Add). Fields: **Type** (only shown in Add — DEBIT / CREDIT / INVESTMENT; reload categories on change), **Amount** (currency-prefixed), **Currency** (INR / USD), **Merchant**, **Category** (filtered to type), **Payment mode**, **Date** + **Time** (Material pickers), **Notes**, read-only **Original SMS** card (hidden in Add). Save validates amount > 0. Add mode inserts via `Repository.addManualTransaction` with a `manual:$UUID` synthetic hash and `isUserEdited = true` so migrations don't touch it.
7. **Investments** — top "Invested this month" card + date-grouped list of INVESTMENT rows. Tap → detail sheet (same as Transactions). Empty state when none tracked.
8. **Categories** (reached from Settings → Manage categories) — list grouped by type with month-to-date totals. **+ Add** FAB → bottom-sheet dialog (name + type + 16-colour swatch row). Tap row to edit (type locked once created). Long-press → Edit / Delete. Delete: empty category → simple confirm; non-empty → choose reassignment target (Other / Uncategorized of same type), bulk-update transactions, drop matching MerchantRules, then delete the row.
9. **Insights** — month picker (◀ / "May 2026" / ▶, next disabled at current month). **Spent** big total + MoM delta (`▲ 23% vs Apr` / "No comparison with Apr"). **By category** card with a custom **donut chart** (`CategoryPieView` — stroked arcs, no chart library) and a legend (coloured dot + name + ₹ + %). Small cards: Received this month (excludes Transfers (CC payment)) + Invested this month.
10. **Settings** — Permissions section (SMS access + Grant), Backfill section (Run backfill + live status from WorkInfo flow), **Notification access** section (for RCS — deep-links to system settings; status shown), Categories section (Manage categories button → Categories screen).

## 11. Background Processing

### 11.1 Ingestion paths
- `SmsBroadcastReceiver` — manifest receiver for `Telephony.Sms.Intents.SMS_RECEIVED_ACTION`. Concatenates multi-part SMS, passes to `Repository.ingestSms` with the original sender. Goes through the strict bank-shortcode filter.
- `MessageNotificationListener` — `NotificationListenerService`. Watches `com.google.android.apps.messaging`, `com.samsung.android.messaging`, `com.android.mms`. Extracts title + best-available body (`MessagingStyle` → `EXTRA_BIG_TEXT` → `EXTRA_TEXT_LINES` → `EXTRA_TEXT`) and calls `ingestSms(... , bypassSenderCheck = true)` since notification titles are friendly names, not shortcodes.
- `BackfillWorker` — one-shot `WorkManager` job. Reads `content://sms/inbox` for the last 90 days, runs the same pipeline. Reports `processed / saved` via `setProgress`; Settings page renders this live.

### 11.2 On-start migrations
All run sequentially on `XpendiqApplication.onCreate` on a background scope. Each is **idempotent** — safe to re-run on every app launch.

1. **Room migration v1 → v2** — adds the `currency TEXT NOT NULL DEFAULT 'INR'` column.
2. **`HashRehashMigration`** — groups existing rows by `(sender, smsBody.trim())`, deletes duplicates (keeps highest id = latest parse), re-hashes survivors with the new `transactionHash(sender, body)` formula. Heals duplicate-row bugs introduced before the parser-independent hash existed.
3. **`SeedMigration`** — adds missing categories (e.g. Subscriptions on older installs), inserts any new seeded `MerchantRule`s by `(pattern, appliesToType)`, then bulk-re-routes existing non-user-edited transactions to match the current rule set (so old "NETFLIX" rows that landed in Entertainment migrate to Subscriptions automatically).
4. **`ColorPaletteMigration`** — updates seeded categories' `colorHex` from the old generic defaults (`#607D8B` / `#4CAF50`) to the per-category `CategoryPalette` colours. Categories that the user has manually recoloured via Manage Categories are left alone.
5. **`StaleTransactionCleanup`** — re-runs today's parser+filter chain over every existing transaction. Rows the current filter would reject (statement reminders, Avl Limit pings, order-status updates, etc.) are deleted and their hashes recorded in `DeletedSmsHash`. Preserves `isUserEdited = true` rows.

### 11.3 Notifications
- Uncategorized count surfaces on the Home **Review** card (not a system notification). The original spec called for a system notification per new uncategorized spend; this is deferred since the Home badge has served fine in practice.

## 12. Privacy & Security
- All data stays on-device. No analytics SDK, no crash reporter, no network calls in v1.
- The Room DB lives in app-private storage. **SQLCipher encryption is not yet wired** — see Future Work §17.
- **Biometric app lock is not yet wired** — see Future Work §17.
- **CSV export is not yet wired** — see Future Work §17.
- Sensitive APIs (`READ_SMS`, `RECEIVE_SMS`, notification listener) are gated behind runtime permissions and an explicit onboarding rationale. Notification access uses the privileged `BIND_NOTIFICATION_LISTENER_SERVICE` route via system Settings; Xpendiq can't grant it on the user's behalf.
- **Release builds strip debug-only surface**: `TestIngestReceiver` lives in `app/src/debug/` and is only included in debug builds. Release builds have no exported intent for fake SMS injection.

## 13. Edge Cases
- **Duplicate SMS / same content via two paths**: dedup key is `sha256(sender + "|" + body.trim())` — content-only, parser-independent. If the same SMS arrives via the broadcast receiver and the notification listener, only the first one is inserted; the second hits `findByHash → DUPLICATE`. If the parser ever interprets the same body differently in the future, `HashRehashMigration` collapses the resulting duplicates.
- **Reversal / refund**: stored as a standalone CREDIT in the Refund category (or via `__CARD_REVERSAL__` marker for card reversals). **Never modifies the original DEBIT.** No auto-linking, no "net" amount.
- **Deleted transactions**: hard-deleted from `transactions`; their `smsBodyHash` is recorded in `DeletedSmsHash` so backfill / live receiver never re-creates them.
- **User-edited transactions**: `isUserEdited` flips to true; subsequent re-parses of the same SMS never overwrite user edits — the hash lookup short-circuits to DUPLICATE.
- **Manual rows**: hash is `manual:$UUID`, `smsBody` and `sender` are null. Cleanup migrations skip rows where `smsBody == null`.
- **USD subscriptions**: stored with `currency = "USD"`, displayed with `$`. Excluded from INR aggregate totals (no FX yet — see §3 Non-Goals).
- **Promotional SMS containing "Rs"**: kept out by Promo / Order-Status / Statement-Reminder guards combined with the "no transaction verb" check.
- **AutoPay / collect requests / Standing Instructions notices**: dropped by the Scheduled guard — these are notifications about future debits, not transactions that happened.
- **CC bill reminders** (`Pay Total Amount Due of Rs X by DATE`): dropped by the Statement-Reminder guard.
- **Standalone Avl Limit pings** (`Avl Limit on your ICICI Bank Credit Card is INR X`): dropped by the balance-report guard. The Avl-Limit suffix on a real spend SMS does NOT trigger the guard because the body also contains "spent".
- **Broker EOD reports**: `GROWW INVEST at EOD ... reported your Fund bal Rs X` — dropped by the balance-report guard.
- **Credit-card bill payments**: stored as CREDIT in **Transfers (CC payment)**; hidden from the Credits list and excluded from "Received this month" totals (it's an internal transfer, not income).
- **Cross-type re-categorisation**: when the user moves a DEBIT row into the Investment category from the Uncategorized inbox, the row's `type` is also flipped. A user-learned rule for that merchant retypes future SMS automatically via `Repository.ingestSms` honouring `category.appliesToType`.
- **RCS-delivered bank SMS**: caught only when the user grants Notification access; historic RCS messages already in Google Messages are unreachable.
- **Merchant name normalization**: card spends arrive with spaces stripped (`VANLAVINOCAFE`), UPI spends arrive with spaces (`VANLAVINO CAFE`). Persist `merchantRaw` verbatim, derive `merchantNormalized = upper().replace(spaces, "")` for matching, display the raw form.
- **SMS app on different SIM / dual SIM**: receiver fires per SIM; no special handling needed.
- **Permission revoked mid-life**: receiver becomes a silent no-op; Home shows a re-grant banner.
- **SBI-Card-only OTPs**: SBI Card sometimes sends *only* an OTP SMS for an online card transaction (no separate "Rs.X spent..." confirmation). Xpendiq correctly drops the OTP — but that means the transaction is invisible to the parser. No fix on Xpendiq's side; bank-side behaviour.

## 14. Open Questions
- Investment sub-categories (Equity / MF / Gold / FD) — keep as a single bucket in v1, revisit if the list gets noisy.
- Multi-account view (e.g., separate "Spouse" account) — out of v1.
- Should the Home "this month" total be calendar month or rolling 30 days? Default: calendar month, with a toggle deferred.

## 15. Milestones (historical — v1 built)
- ✅ **M1 — Skeleton**: project setup, Room schema, seed data, Categories CRUD UI.
- ✅ **M2 — Parser**: 14 extractors + filter chain + unit tests on a corpus of real anonymised SMS.
- ✅ **M3 — Live ingestion**: BroadcastReceiver + NotificationListenerService (RCS) + dedupe.
- ✅ **M4 — UI**: Home, Transactions (Spends/Credits tabs), Investments page, Uncategorized Inbox, Transaction Detail with edit/delete, Add transaction.
- ✅ **M5 — Backfill + Insights**: WorkManager scan + donut chart + DeletedSmsHash guard + StaleTransactionCleanup.
- ⏳ **M6 — Polish**: encryption, biometric lock, CSV export → see Future Work §17.

## 16. Success Metrics (for self-evaluation)
- ≥ 90% of transactional SMS from the seeded sender allowlist produce a Transaction record (debit, credit, or investment).
- ≥ 70% of created Transactions are auto-categorized (not Uncategorized) after the user has manually categorized 20 merchants.
- Uncategorized inbox (spends + credits combined) can be cleared in < 30 seconds for a typical week (≈ 10 items).
- 100% of user edits and deletions survive a backfill re-scan.

## 17. Future Work

In rough priority order:

### 17.1 Biometric app lock
- Use `BiometricPrompt` (androidx.biometric) with `BIOMETRIC_STRONG | DEVICE_CREDENTIAL` fallback.
- Lock state held in `XpendiqApplication`; MainActivity inserts a lock screen Fragment between launch and the bottom-nav UI when locked.
- Settings toggle: **App lock** with sub-options "Immediately" / "After 1 minute" / "After 5 minutes" — last used time persisted to SharedPreferences.
- Onboarding skips this; it's an opt-in setting.

### 17.2 SQLCipher database encryption
- Replace the default Room SupportSQLiteOpenHelperFactory with `net.zetetic:sqlcipher-android`.
- Passphrase generated on first run, stored in the **Android Keystore** (`MasterKey` + `EncryptedSharedPreferences`).
- Migration path: detect plaintext DB, re-encrypt in place on next launch (one-shot, on a background thread). All current migrations (`HashRehashMigration`, `SeedMigration`, etc.) need to run *after* the DB is decrypted, so they sit behind the SQLCipher init.
- Cost: SQLCipher adds ~7 MB to the APK and a small perf overhead on each query. Acceptable for a finance app.

### 17.3 CSV export
- Settings → **Export to CSV** button → `ACTION_CREATE_DOCUMENT` (Storage Access Framework). User picks the location; we write a `text/csv` file.
- Columns: id, occurredAt (ISO 8601), type, currency, amountPaise/100, paymentMode, merchantRaw, categoryName, accountTail, notes, isUserEdited, smsBody.
- Filter UI: date range (default last 30 days), type checkboxes (debits / credits / investments). Default-on for the user's most common slice.
- Streamed write, not held in memory — works fine for 10k+ rows.

### 17.4 Ignored Senders management
- DAO and entity already exist (`IgnoredSender`, `IgnoredSenderDao`). Need a Settings sub-screen to add / remove sender IDs.
- Easy entry point: a row swipe-action on Uncategorized inbox "Ignore this sender" + an undo Snackbar.
- Once added, the live receiver and backfill skip all SMS from that sender.

### 17.5 Transactions list polish (deferred from §10.3)
- Filter chips at the top: category, payment mode, date range, currency.
- Search by merchant (filters the date-grouped list as you type).
- Long-press → multi-select mode with batch Delete and batch Categorize. Currently you delete one row at a time from the detail sheet.

### 17.6 Parser long-tail pass
- After a few weeks of real-world usage, run `samples/unparsed.py` against the user's actual data, identify the next most common missed shape, and add an extractor. Likely candidates: ICICI savings-account debit alerts, Axis Bank, Kotak Bank, Yes Bank, neobank issuers.
- Also: add an "unparseable" log table that records (sender, body, timestamp) for messages from allowlisted senders that no extractor matched. Periodically inspect to discover new shapes without needing to grep SMS inbox manually.

### 17.7 Uncategorized-spend notification
- Per spec §11.3 — original plan. Rate-limited (≤ 1 per hour), tappable to deep-link into the Uncategorized inbox.
- Quiet hours support (10pm–8am suppressed).

### 17.8 Larger backfill window / re-scan
- Currently `BACKFILL_DAYS = 90`. Add a Settings dropdown: 90 / 180 / 365 days / All.
- Useful for picking up older SBI Card SMS that fell outside the default window (see §13 — SBI Card now uses RCS so historic SMS still exist in inbox but predate the cutoff).

### 17.9 Investment sub-categories
- Currently a single Investment bucket. If the user accumulates many SIPs across schemes, add a free-form sub-category (Equity / MF / Gold / FD) editable from Manage Categories.

### 17.10 Cleanup
- Delete the now-unreferenced `res/layout/item_category_bar.xml` (the Insights screen moved from bars → donut in §10.9).
- Promote the seeded merchant allowlist to a versioned JSON in `assets/` so it can be edited without touching `DatabaseSeeder.kt`.
