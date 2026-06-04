# Auto Ledger Design

## Goal

Build an Android app named "自动账本" that keeps a durable personal ledger on the phone, automatically captures spending signals from installed payment-related apps where Android permits it, classifies entries with a detailed category system, and syncs the user's data to Supabase for phone replacement and backup.

## Scope For The First Version

The first version targets Android only. It must be useful as a standalone app even if cloud sync is unavailable.

Included:

- Local ledger backed by Room/SQLite.
- App name "自动账本".
- Home, records, insights, sources, and settings screens.
- Detailed built-in category tree with first, second, and third level categories.
- Notification listener for payment, bank, delivery, shopping, ride-hailing, telecom, subscription, and other spending notifications.
- SMS receiver for bank and payment SMS messages after the user grants permission.
- Installed-app source discovery to show likely spending sources on the phone.
- Pending confirmation flow for automatically captured entries.
- Supabase sync using the provided project URL and publishable key.
- A SQL setup script the user can run in Supabase SQL Editor.
- CSV export as a backup and migration tool.
- APK build and device install through adb.

Not included in the first version:

- iOS support.
- Bank-card password login, scraping, or bypassing any app's security model.
- Guaranteed capture of payments when the source app does not expose usable notification or SMS text.
- Multi-user household accounting.
- Receipt OCR.

## Product Behavior

The app stores all ledger entries locally first. Automatic captures create pending entries. The user confirms, edits, ignores, or deletes them. Confirmed entries drive the main totals and charts. Pending entries remain visible so silent capture failures and parser uncertainty are obvious.

The UI is built around daily use:

- Home: monthly spending, income, net amount, pending count, recent records, category highlights.
- Records: searchable transaction list with filters for status, source, category, account, and date range.
- Insights: spending by category, merchant, account, source app, payment method, and month trend.
- Sources: notification-listener status, SMS permission status, installed likely spending apps, and capture coverage notes.
- Settings: Supabase sync key, sync status, SQL setup help, CSV export, and permission shortcuts.

## Data Model

The ledger entry is the core record:

- `id`: stable UUID generated locally.
- `type`: expense, income, transfer, refund, adjustment.
- `status`: pending, confirmed, ignored.
- `amountCents`: signed amount in cents.
- `currency`: default CNY.
- `occurredAt`: transaction timestamp.
- `merchant`: merchant or counterparty.
- `title`: short display title.
- `categoryPath`: slash-separated category path, such as `餐饮/外卖/工作餐`.
- `account`: cash, Alipay, WeChat Pay, bank card, credit card, or custom.
- `paymentMethod`: source-specific method when known.
- `sourceKind`: manual, notification, sms, import, sync.
- `sourcePackage`: Android package name when captured from an app notification.
- `sourceAppName`: visible app name.
- `rawText`: original notification or SMS text for user review.
- `confidence`: parser confidence from 0 to 100.
- `note`: user note.
- `tags`: comma-separated user tags.
- `createdAt`, `updatedAt`, `syncedAt`: audit timestamps.
- `isDeleted`: tombstone for sync.

Categories are code-defined in the first version and editable categories can be added later. The initial category system covers food, transport, shopping, housing, utilities, medical, education, entertainment, travel, digital services, family, social, finance, income, transfer, refund, and uncategorized items.

## Automatic Capture

Android notification access is the primary capture channel. The app registers a `NotificationListenerService` and parses posted notifications. SMS capture is secondary and only runs after explicit SMS permission.

The parser is conservative:

- It requires an amount-like pattern before creating an entry.
- It classifies by source app, keyword, merchant text, and context.
- It ignores obvious non-transaction texts such as promotions, login codes, and balance-only reminders.
- It marks lower-confidence entries as pending with the raw text visible.

The installed-app scanner does not read private app data. It only lists packages installed on the phone and highlights apps likely to create spending notifications, such as Alipay, WeChat, UnionPay, banks, shopping apps, food delivery apps, ride-hailing apps, telecom apps, travel apps, and subscription/content apps.

## Supabase Sync

The app reads Supabase connection values from local, untracked build configuration:

- `SUPABASE_URL`
- `SUPABASE_PUBLISHABLE_KEY`

The publishable key is not an admin secret, but it still must not be committed to GitHub. The project needs a one-time SQL script run in Supabase SQL Editor.

The first sync design avoids asking the user to share admin secrets. The app generates a local sync key. Records are synced with that sync key, and the same key can be entered on another phone to restore the ledger. Row-level security policies restrict anonymous access to rows whose `owner_key` matches the `x-owner-key` request header sent by the app.

This is not as convenient as full email/password login, but it is cheap, stable, portable, and avoids storing privileged Supabase credentials in the app.

## Error Handling

- If notification permission is missing, Sources and Settings show a direct shortcut to Android notification-listener settings.
- If SMS permission is denied, the app continues with notification and manual records.
- If Supabase tables are missing, sync reports the missing setup and keeps all local data.
- If network sync fails, entries remain local and pending sync.
- If parsing is uncertain, the entry is pending and editable rather than silently confirmed.

## Verification

Implementation must verify:

- Parser tests for Alipay, WeChat, bank card, SMS, refund, transfer, promotion-ignore, and subscription cases.
- Category tests for common merchants and keywords.
- Room DAO tests or JVM-level repository tests where Android instrumentation is not practical.
- Android build.
- APK package metadata.
- APK install to the connected device with adb.
