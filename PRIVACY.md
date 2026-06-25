# Privacy Policy — Xpendiq

**Effective date:** 25 June 2026
**Last updated:** 25 June 2026

Xpendiq ("the app") is a personal expense tracker for Android, developed by
Kanishk Sonkusre ("the developer", "we"). This policy explains what the app
accesses, why, and what happens to that information.

## The short version

**Xpendiq works entirely on your device. It has no servers, no account, no
analytics, and no ads. Your financial information is never uploaded, sold, or
shared with the developer or any third party.**

There is no network connection used to transmit your data anywhere. Everything
the app reads is processed on your phone and stored only on your phone.

## Transparency

You don't have to take our word for it. Xpendiq's source code is public and can
be inspected at **https://github.com/ka1z3n/xpendiq**, so anyone can verify how
the app handles data and confirm the claims made in this policy. (The code is
published under a source-available license; see the repository's `LICENSE` for
the terms under which it may be used.)

## What the app accesses, and why

Xpendiq is distributed in two builds. The data it accesses depends on which one
you installed:

### Notification access (Google Play build)

The Google Play version of Xpendiq uses Android's **notification access**
(`NotificationListenerService`) to read notifications **only** from messaging
apps — Google Messages, Samsung Messages, and the AOSP Messaging app.

It does this to detect **bank transaction alerts** (for example, UPI payments,
card spends, and bank credits) so it can record them as transactions for you
automatically.

- Only messages that look like bank transaction alerts are parsed and saved.
- OTPs, one-time passcodes, and promotional messages are filtered out and not
  stored.
- Notifications from any other app are ignored entirely.

### SMS access (off-Play "full" build only)

The "full" build distributed **outside** Google Play (for example as a direct
APK from the project's source repository) requests the `RECEIVE_SMS` and
`READ_SMS` permissions to read incoming and existing **bank SMS** for the same
purpose. The Google Play build does **not** include these permissions.

### Other permissions

- **Notifications (`POST_NOTIFICATIONS`)** — to show you the app's own status
  notifications, if any. Not used to read anything.
- **Biometric / device lock** — if you enable the optional app lock, unlocking
  uses your device's biometric or screen-lock. This is handled entirely by
  Android; the app never sees, receives, or stores your biometric data.

## How your information is used and stored

Transactions parsed from your bank alerts are stored in a **local database on
your device**. This includes details such as amount, date, merchant or payee
name, payment method, and the original alert text — all kept locally so the app
can show you your spending.

This data is:

- **Never transmitted off your device.** The app makes no network calls to send
  your information anywhere.
- **Never shared** with the developer, advertisers, analytics providers, or any
  other third party.
- **Not used** for advertising, profiling, or any purpose other than showing you
  your own transactions inside the app.

## Backups

If you have Android's automatic backup enabled on your device, your app data may
be included in your own device backup stored in **your personal Google account**,
under Google's control — not the developer's. You can disable this in your
device's system backup settings.

## Data retention and deletion

You are in full control of your data:

- Delete individual transactions inside the app at any time.
- **Uninstalling the app permanently removes all of its data** from your device.

Because the developer never receives your data, there is nothing for us to
retain or delete on our side.

## Children

Xpendiq is not directed at children and is intended for users managing their own
finances.

## Changes to this policy

If this policy changes, the updated version will be published at this same
location with a new "Last updated" date.

## Contact

Questions about this policy or the app's privacy practices:

**Kanishk Sonkusre** — shadowshredder77@gmail.com
