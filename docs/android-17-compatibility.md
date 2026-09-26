# Android 17 (API 37) Compatibility Comparison

This document provides a technical comparison between the original Shizuku release (v13.6.0) and our modernized fork (v13.6.0.r39+) when running on Android 17 (Cinnamon Bun / API 37) Canary.

## The Core Issue

In the May 2026 update for Android 17, Google introduced significant changes to hidden APIs to support Virtual Devices and improve security. Specifically, method signatures within `IPackageManager` and `IPermissionManager` were modified to include a `deviceId` parameter for multi-device awareness.

### Affected APIs
- `IPermissionManager.grantRuntimePermission`
- `IPermissionManager.revokeRuntimePermission`
- `IPermissionManager.checkPermission`
- `IPackageManager.getInstalledPackages`
- `IPackageManager.getPackageInfo`
- `IPackageManager.getApplicationInfo`

Because Xhizuku relies on these hidden APIs to operate, the legacy implementation fails with `NoSuchMethodError` when running on API 37+.

## Original Shizuku (v13.6.0)

When running the original Shizuku on Android 17, the server process initializes but encounters critical failures during permission evaluation or package listing.

### Behavior
- Permission grants/revocations fail silently or crash the server.
- Package listing returns empty results or triggers crashes in client apps.
- `NoSuchMethodError` is frequently logged in Logcat when interacting with system services.

## Xhizuku (Modernized Fork)

Xhizuku implements a high-performance, dynamic reflection fallback via `Android17Compat.java`.

### Technical Implementation
- **Dynamic Method Resolution:** Identifies if target methods (e.g., `grantRuntimePermission`) expect the new `deviceId` parameter and injects `Context.DEVICE_ID_DEFAULT` (0) accordingly.
- **Caching Layer:** Memoizes resolved `Method` objects and system service proxies to eliminate reflection overhead, ensuring near-native performance.
- **Service Integration:** `ShizukuService` uses `Android17Compat` for all critical system API calls, guaranteeing stability on API 37+.

## Conclusion

Original Shizuku is incompatible with Android 17 due to the Virtual Device API shift. Xhizuku's `Android17Compat` layer restores full functionality, ensuring it remains the standard for elevated privilege access on modern Android versions.
