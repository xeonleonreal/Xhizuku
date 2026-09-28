## Fork additions
This is list of additions that will be updated every major update

### Nightzuku
- Jetpack Compose manager UI with Material 3 Expressive components, motion, switches, and rounded icon treatment.
- Android 16/17 target work with current preview SDK/build tooling in this fork.
- ADB Modules screen for installing and managing ZIP modules.
- Module features: `module.prop`, banner, enable/disable switch, `action.sh`, policy-gated `service.sh`, local WebUI, delete, path checks, size limits, output limits, and last-run logs.
- Module policy settings: Safe mode, Full access, and background action control.
- Debug test module under `test-modules/adb-test-module`.
### Xhizuku
#### Update 1.0.0:
- Onboarding wizard, that helps users to migrate from shizuku
- Magisk style menu
- Theme color overide in Xhizuku
- XStatus: CPU, GPU, memory and swap usage in an ongoing notification
- Expandable card descriptions on the status page (tap the arrow to show or hide long explanations)
#### Update 2.0.0:
- In-app updater checks latest update on GitHub
- Server info, shows information about the server. Useful for reporting bugs
- Auto-restart. When the server dies it will try to auto start again.
- Better auth system! Now you can deny/deny for set time/allow for set time/allow once/allow
a