# Agenda

Shows a merged, read-only agenda on your Light Phone, pulled from any number of ICS feed
URLs - Outlook, Proton Calendar, Google, iCloud, or anything else that can hand you a
"subscribe to this calendar" link.

## Why this exists

LightOS's built-in Calendar app only offers Google and iCloud as connection options. Outlook
and Proton Calendar can't be added there, and for different reasons:

- **Outlook/Microsoft 365 has never implemented CalDAV** - Microsoft uses its own protocols
  (Exchange Web Services, Microsoft Graph) instead, so even a fully generic "CalDAV" field
  wouldn't reach it.
- **Proton Calendar doesn't support CalDAV either**, by design - it's built around end-to-end
  encryption, which CalDAV's model doesn't accommodate. Proton's own answer for
  interoperability is a **read-only ICS share link**, not a syncable protocol.

That read-only ICS link is exactly what both providers *do* offer, and what this tool reads.
That's also this tool's one real limitation: it's **one-way**. Nothing you do here writes back
to Outlook, Proton, Google, or iCloud - it's a display surface, not a sync client. Two-way sync
(and definitely showing up inside LightOS's own Calendar app) isn't possible without
Light adding new permissions this SDK doesn't currently allow (there's no calendar or
account-manager permission on the tool metadata allowlist - see
[`docs/tool_metadata`](../../docs/tool_metadata)) - worth raising as a feature request if you
want it upstream.

## Getting an ICS URL

**Outlook / Outlook.com / Microsoft 365:** Calendar → Settings (gear icon) → **Shared
calendars** → **Publish a calendar** → pick the calendar and **Can view all details** → copy
the **ICS** link (not the HTML one).

**Proton Calendar:** open the calendar's settings → **Share** → **Share publicly** (or **Share
privately** for a link that isn't guessable) → copy the link and use it here as-is.

**Google Calendar:** Settings → pick the calendar → **Integrate calendar** → copy **Secret
address in iCal format** (for a private calendar) or **Public address in iCal format**.

**iCloud Calendar:** on icloud.com, hover the calendar → **⋯** → **Public Calendar** → copy the
`webcal://` link (this tool rewrites `webcal://` to `https://` automatically).

Any of these is a plain URL ending in `.ics` (or close to it) - paste it into "Add Calendar" in
the tool along with a short label like "Work" or "Personal".

## Building and installing

Same pattern as this repo's other example tools:

```bash
./gradlew :examples:agenda:assembleDebug
```

or, with your Light Phone connected over USB and developer mode/USB debugging on (see
[`examples/pulse`](../pulse/README.md#step-1-turn-on-your-phones-developer-settings-one-time)
for that one-time setup):

```bash
./gradlew :examples:agenda:installDebug
```

## Notes on what's parsed

The ICS parser here covers real-world Google/Outlook/Proton feeds (`SUMMARY`, `DTSTART`/
`DTEND`, all-day events, a practical `RRULE` subset, `EXDATE`) without pulling in a full
iCalendar library - not something the SDK's dependency allowlist would clear anyway. A few
known gaps, called out in [`IcsParser.kt`](src/main/kotlin/com/thelightphone/agenda/IcsParser.kt):
non-standard `VTIMEZONE` definitions aren't honored (`TZID` is looked up as a plain IANA zone
name, which is what every provider tested actually emits), a single modified instance of a
recurring series (`RECURRENCE-ID`) isn't special-cased, and `BYDAY` is only honored for
`FREQ=WEEKLY`. Recurring events are expanded 14 days ahead - the same horizon the agenda
itself shows.
