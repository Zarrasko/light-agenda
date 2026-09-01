# Light SDK demo tools

| Module | Package | Description |
|--------|---------|-------------|
| `pulse` | `com.thelightphone.pulse` | activities, wellness, and workout scheduling via intervals.icu |

## How to run on device

```bash
./gradlew :examples:pulse:installDebug
adb shell am start -n com.thelightphone.pulse/com.thelightphone.sdk.LightActivity
```
