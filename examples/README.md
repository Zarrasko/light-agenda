# Light SDK demo tools

| Module | Package | Description |
|--------|---------|-------------|
| `agenda` | `com.thelightphone.agenda` | merges read-only Outlook/iCloud/Proton/Google calendar feeds into one agenda, with reminders and clickable links |

## How to run on device

```bash
./gradlew :examples:agenda:installDebug
adb shell am start -n com.thelightphone.agenda/com.thelightphone.sdk.LightActivity
```
