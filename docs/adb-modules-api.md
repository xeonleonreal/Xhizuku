# Xhizuku ADB Modules API

ADB Modules are ZIP packages installed into Xhizuku private app storage and executed through the currently active Xhizuku server. If Xhizuku is running from ADB, module scripts run with ADB shell privileges. If Xhizuku is running from root, scripts run with root privileges.

This is not a root overlay system. It is a Xhizuku-backed module runner for actions, WebUI, service hooks, and controlled ADB/root shell access.

## Package Format

A module is a `.zip` file with `module.prop` at the ZIP root.

```text
module.zip
├── module.prop
├── banner.png
├── action.sh
├── service.sh
└── webui/
    └── index.html
```

All paths must be relative. Absolute paths and `..` traversal are rejected during install.

## module.prop

Required fields:

```properties
id=my-module
name=My Module
version=1.0
versionCode=1
author=Author
description=Short description
```

Optional fields:

```properties
banner=banner.png
webui=webui
action=action.sh
```

Rules:

- `id` must match `[A-Za-z][A-Za-z0-9._-]{1,63}`.
- `banner` can point to `.png`, `.jpg`, `.jpeg`, or `.webp`.
- If `banner` is omitted, Xhizuku checks `banner.png`, `banner.jpg`, `banner.jpeg`, then `banner.webp`.
- If `webui` is omitted, Xhizuku checks `webroot`, `webui`, then `web`.
- WebUI is available only when `<webui>/index.html` exists.
- `action` defaults to `action.sh`.
- `service.sh` is detected automatically.

## Install Behavior

Install flow:

1. User selects a module ZIP with Android file picker.
2. Xhizuku copies it into cache.
3. Xhizuku validates `module.prop`.
4. Xhizuku extracts into a staging directory.
5. Xhizuku rejects unsafe paths.
6. Xhizuku marks `.sh` files executable.
7. Xhizuku replaces any existing module with the same `id`.
8. Xhizuku stores the module under app-private storage.

Safety limits:

- Max ZIP entries: `2048`.
- Max extracted size: `200 MB`.
- Script output retained in memory/log: last `64 KB` per stream.
- Script timeout: `120 seconds`.

## Runtime Environment

Scripts run through Xhizuku server process creation. The command is:

```sh
sh /path/to/module/action.sh
```

or:

```sh
sh /path/to/module/service.sh
```

Working directory is the module directory.

Environment variables:

```sh
MODDIR=/data/user/0/xeonleon.xhizuku/files/adb_modules/<id>
ASH_STANDALONE=1
SHIZUKU_MODULE_ID=<id>
SHIZUKU_MODULE_MODE=safe|full
SHIZUKU_MODULE_BACKGROUND=0|1
```

Use `MODDIR` for all module-local files. Do not assume root paths such as `/data/adb/modules`.

## Actions

`action.sh` is a manual user action launched from the module card.

Action result:
- stdout/stderr are shown in a dialog.
- Last output is written to `logs/action-last.log` inside the module directory.
- Timeout (120s) returns exit code `124`.

Minimal `action.sh`:

```sh
#!/system/bin/sh
echo "module=$SHIZUKU_MODULE_ID"
id
```

## Services

`service.sh` is the background hook.

Execution policy:
- **Safe mode**: Blocked.
- **Full access mode**: Allowed if "Allow background actions" is enabled.
- Service scripts run once per Xhizuku binder session.
- Last output is written to `logs/service-last.log`.
- Timeout (120s) returns exit code `124`.

## WebUI

WebUI is loaded from `webui/index.html`.

Current WebView policy:
- JavaScript, DOM storage, and local file access enabled.
- Network access blocked unless Custom/Full mode enables it.
- `window.Shizuku` is exposed for enabled module-local WebUI if policy allows.

### JavaScript-to-Shell Bridge

The `window.Shizuku` object allows WebUI to interact with the shell.

#### Module Info

```javascript
const info = JSON.parse(window.Shizuku.getModuleInfo());
console.log(info.id);         // e.g. "my-module"
console.log(info.enabled);    // true
```

#### Shell Execution

```javascript
const result = JSON.parse(window.Shizuku.exec("id"));
if (result.ok) {
    console.log(result.stdout);
}
```

Advanced execution:

```javascript
const result = JSON.parse(window.Shizuku.execWithOptions("pwd", JSON.stringify({
  timeoutSeconds: 30,
  cwd: "webui"
})));
```

Rules:
- `stdin` is limited to 64 KB.
- stdout/stderr return the last 64 KB per stream.
- `cwd` must be within the module directory.

### Full Trust

Full Trust is a per-module override. Long-press a module card to toggle.

Trusted modules:
- Bypass global Action/Service/Background/WebUI gates.
- Skip ReCommand prompts.
- Can use `download()` and WebView internet together.

### WebUI Asset Loader

`window.Shizuku.download(url, relativeWebPath)` downloads HTTPS assets to the module WebUI root.
- Max file size: 20 MB.
- Cannot overwrite `index.html`.

## Enable, Disable, Delete

Disabling creates a `disable` file in the module directory, blocking actions and services. Deleting removes the entire module directory.

## Test Module

The repository includes a test module:

```text
test-modules/adb-test-module.zip
```

It contains:

- `module.prop`
- `banner.png`
- `action.sh`
- `service.sh`
- `webui/index.html`

Expected action output includes the current UID, SDK version, module id, and module mode.

## Current Scope

Implemented:

- ZIP install.
- Module metadata parsing.
- Path traversal protection.
- Size and entry limits.
- Enable/disable/delete.
- Banner rendering.
- WebUI rendering.
- HTTPS WebUI asset loading.
- WebUI HTTPS file download into the module WebUI root.
- Manual `action.sh`.
- Policy-gated `service.sh`.
- One service run per Xhizuku binder session.
- Last action/service logs.
- Direct JavaScript-to-shell bridge with optional timeout/stdin/cwd/env.

Not implemented:

- Systemless filesystem overlays.
- Magisk/KSU mount semantics.
- Long-running service supervision.

Those are separate features and should not be implied by the current ADB module API.
