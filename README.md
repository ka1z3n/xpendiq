# Spendy

A privacy-first Android expense tracker that reads transactional bank SMS (and RCS) and turns them into a categorized spend log. Everything runs on-device; nothing leaves the phone.

Designed for Indian bank SMS (HDFC, SBI, ICICI, Axis, Kotak, plus mutual-fund and PayU). USD is also recognized for foreign subscriptions.

## Features

- **Zero-friction ingestion** — `SmsBroadcastReceiver` for live SMS, `BackfillWorker` for the last 90 days, `NotificationListenerService` for RCS messages from Google Messages / Samsung Messages.
- **Parser** — 14 issuer-aware extractors + a tight generic fallback. Filters out OTPs, statement reminders, AutoPay notices, balance reports, order-status updates, and promos.
- **Categorization** — seeded merchant rules (Food, Groceries, Transport, Subscriptions, etc.) with a `MerchantRule` table the app keeps adding to as you correct Uncategorized rows ("Always categorize from X this way").
- **Cross-type re-categorization** — move a parsed-as-DEBIT row into Investment; the row's `type` flips and future SMS from that merchant retype automatically.
- **Five screens** — Home (totals + top categories + recent), Transactions (date-grouped, tinted category chips), Investments, Insights (month picker + donut chart + MoM delta), Settings (permissions + backfill + notification access + manage categories).
- **Manual add** — for the rare bank that doesn't send SMS at all.

For the full spec, design choices, and future-work backlog, see [SPEC.md](SPEC.md).

## Build & install

```bash
# debug build
./gradlew :app:assembleDebug

# run unit tests
./gradlew :app:testDebugUnitTest

# install on a connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Java 17 + Android SDK with `compileSdk 36`, `minSdk 26`. The Gradle wrapper uses the bundled toolchain (JBR 21 if you launch from Android Studio; set `JAVA_HOME` to your Studio's `jbr/` when running from the CLI).

## Project layout

```
app/
├── src/main/java/com/example/spendy/
│   ├── data/         # Room entities, DAOs, repositories, migrations
│   ├── parser/       # SmsFilter + SmsParser + 14 extractors
│   ├── categorizer/  # MerchantRule lookup
│   ├── sms/          # SMS broadcast + RCS notification listener
│   ├── work/         # WorkManager backfill
│   ├── ui/           # Fragments: home, transactions, investments,
│   │                 #   insights, settings, categories,
│   │                 #   uncategorized, edit, onboarding
│   └── util/         # Currency, date, hashing, permissions helpers
├── src/debug/        # debug-only TestIngestReceiver for adb injection
├── src/test/         # Parser unit tests + corpus coverage test
└── src/main/res/     # Layouts, drawables, menus, navigation graph
samples/              # Analysis scripts (Python) — personal SMS exports
                      #   are gitignored
SPEC.md               # Living product + technical spec
```

## Privacy

No network calls. No analytics. No crash reporter. Bank SMS bodies are stored locally in Room (SQLCipher encryption planned — see SPEC §17.2). The `TestIngestReceiver` is gated to debug builds only — release builds have no exported intent for fake-SMS injection.

## Testing pipeline locally

Debug builds register a `TestIngestReceiver` that lets you inject a fake bank SMS through the full filter→parser→categorizer→DB pipeline via `adb`:

```bash
# SMS-style (real shortcode sender):
adb shell "am broadcast -a com.example.spendy.TEST_INGEST \
    -n com.example.spendy/.sms.TestIngestReceiver \
    --es sender 'VM-HDFCBK-T' \
    --es body 'Sent Rs.250.00 From HDFC Bank A/C *1234 To TEST On 23/05/26 Ref 999'"

# RCS-style (friendly sender, bypassSenderCheck=true):
adb shell "am broadcast -a com.example.spendy.TEST_INGEST \
    -n com.example.spendy/.sms.TestIngestReceiver \
    --es sender 'SBI Card' \
    --es body 'Rs.500 spent on your SBI Credit Card ending 9999 at TEST on 23/05/26.' \
    --ez bypass true"

# Tail the logs:
adb logcat SpendyTestIngest:V SpendyNotifListener:V *:S
```

## License

Personal project. No license declared yet.
