# Screenshots

Place app screenshots here. They are referenced by the **Screenshots** section
of the top-level [README](../../README.md).

Expected files (PNG or WebP):

- `home.png`
- `transactions.png`
- `insights.png`
- `settings.png`

## Before you commit

This is a **public** repository. Only commit screenshots taken with **demo
data** — never real bank SMS, transaction amounts, account/card numbers, VPAs,
payee names, or merchant names.

Tips:
- Use an emulator or a fresh install seeded with the debug `TEST_INGEST` receiver
  (see the main README) so the data is fake.
- The in-app screen lock sets `FLAG_SECURE`; turn the lock off in Settings before
  capturing, otherwise `adb` / system screenshots will be blank.
- Keep portrait screenshots a consistent size (e.g. 1080px wide) so the table
  renders evenly.
