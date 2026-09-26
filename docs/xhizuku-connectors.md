# Xhizuku Connectors API

Xhizuku Connectors is an experimental feature (available in **Lab Features**) that allows third-party apps, referred to as "Activators", to securely query the command required to start the Xhizuku server locally.

## Why Xhizuku Connectors?

Often, enthusiasts discover Local Privilege Escalation (LPE) exploits (e.g., Dirty Pipe, FOTA exploits, mktimer) that can be used to gain temporary elevated privileges without a PC or full root access. Xhizuku Connectors provides a standardized interface for these "Activators" (usually small 1MB APKs) to retrieve the internal Xhizuku startup command, apply their specific exploit, and bootstrap the Xhizuku server directly on the device.

## Prerequisites

For safety, this feature is **disabled by default**. 
To use it, the user must navigate to the Xhizuku Settings -> **Lab Features**, enable **Xhizuku Connectors**, and accept the safety warning.

## How to use

When Xhizuku Connectors is enabled, Xhizuku exposes a local, exported `ContentProvider` at the following URI:

```
content://xeonleon.xhizuku.connector
```

### Querying the Provider

You can query this URI to retrieve a `Cursor` containing exactly one row and one column named `command`.

#### Android Java/Kotlin Example:

```kotlin
val uri = Uri.parse("content://xeonleon.xhizuku.connector")
contentResolver.query(uri, null, null, null, null)?.use { cursor ->
    if (cursor.moveToFirst()) {
        val commandIndex = cursor.getColumnIndex("command")
        if (commandIndex != -1) {
            val xhizukuCommand = cursor.getString(commandIndex)
            // Execute the command using your local exploit payload
            Log.d("Activator", "Got command: $xhizukuCommand")
        }
    }
}
```

#### Shell Script Example:

```bash
OUTPUT=$(content query --uri content://xeonleon.xhizuku.connector)
if [[ $OUTPUT == *"command="* ]]; then
    CMD=$(echo "$OUTPUT" | grep -o 'command=.*' | cut -d= -f2-)
    # Run the command with elevated privileges
    eval "$CMD"
else
    echo "Xhizuku Connectors is not enabled or Xhizuku is not installed."
fi
```

### Return Values

- If **Xhizuku Connectors** is **enabled**, the provider will return the full shell command string required to start the internal Xhizuku server.
- If **Xhizuku Connectors** is **disabled** (or the user has not accepted the warning), the provider will return `null` or an empty result set, depending on the client's query mechanism.
