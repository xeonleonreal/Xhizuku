package rikka.shizuku.shell;

import android.app.ActivityManagerNative;
import android.app.IActivityManager;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.system.Os;
import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Objects;

import rikka.shizuku.shell.BuildConfig;
import rikka.hidden.compat.PackageManagerApis;
import stub.dalvik.system.VMRuntimeHidden;

public class TapiManager {

    private static final String BINDER_DESCRIPTOR = "moe.shizuku.server.IShizukuService";

    private static final int TRANSACTION_getVersion = 3;
    private static final int TRANSACTION_getUid = 4;
    private static final int TRANSACTION_newProcess = 8;
    private static final int TRANSACTION_exit = 101;
    private static final int TRANSACTION_getFlagsForUid = 106;
    private static final int TRANSACTION_updateFlagsForUid = 107;

    private static final int FLAG_ALLOWED = 1 << 1;
    private static final int MASK_PERMISSION = FLAG_ALLOWED | (1 << 2);

    private static String[] args;
    private static String command;
    private static String callingPackage;
    private static Handler handler;
    private static IBinder shizukuBinder;

    private static final Binder receiverBinder = new Binder() {

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code == 1) {
                IBinder binder = data.readStrongBinder();
                String sourceDir = data.readString();
                if (binder != null) {
                    handler.post(() -> onBinderReceived(binder));
                } else {
                    System.err.println("tapi: Xhizuku server is not running");
                    System.err.flush();
                    System.exit(1);
                }
                return true;
            } else if (code == 2) {
                System.err.println("tapi: TAPI is disabled. Enable it in Lab Features of the Xhizuku app.");
                System.err.flush();
                System.exit(1);
                return true;
            }
            return super.onTransact(code, data, reply, flags);
        }
    };

    private static void requestForBinder() throws RemoteException {
        Bundle data = new Bundle();
        data.putBinder("binder", receiverBinder);

        Intent intent = new Intent("rikka.shizuku.intent.action.REQUEST_BINDER")
                .setPackage(BuildConfig.MANAGER_APPLICATION_ID)
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                .putExtra("data", data);

        IBinder amBinder = ServiceManager.getService("activity");
        IActivityManager am;
        if (Build.VERSION.SDK_INT >= 26) {
            am = IActivityManager.Stub.asInterface(amBinder);
        } else {
            am = ActivityManagerNative.asInterface(amBinder);
        }

        try {
            if (Build.VERSION.SDK_INT >= 36) {
                Intent activityIntent = new Intent("rikka.shizuku.intent.action.REQUEST_BINDER")
                        .setPackage(BuildConfig.MANAGER_APPLICATION_ID)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra("data", data);
                am.startActivityAsUser(null, callingPackage, activityIntent, null, null, null, 0, 0, null, null, Os.getuid() / 100000);
            } else if (Build.VERSION.SDK_INT >= 30) {
                java.lang.reflect.Method method = findBroadcastMethod(am);
                if (method == null) {
                    throw new RuntimeException("Cannot find broadcastIntentWithFeature on " + am.getClass());
                }
                Class<?>[] paramTypes = method.getParameterTypes();
                Object[] invokeArgs = new Object[paramTypes.length];
                invokeArgs[0] = null;
                invokeArgs[1] = null;
                invokeArgs[2] = intent;
                int intIndex = 0;
                int booleanIndex = 0;
                for (int i = 3; i < paramTypes.length; i++) {
                    Class<?> t = paramTypes[i];
                    if (t == boolean.class) {
                        invokeArgs[i] = booleanIndex++ == 0;
                    } else if (t == int.class) {
                        invokeArgs[i] = intIndex++ == 0 ? -1 : 0;
                    } else if (t == long.class) {
                        invokeArgs[i] = 0L;
                    } else {
                        invokeArgs[i] = null;
                    }
                }
                try {
                    method.invoke(am, invokeArgs);
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException("broadcastIntentWithFeature invocation failed", e);
                }
            } else {
                am.broadcastIntent(null, intent, null, null, 0, null, null,
                        null, -1, null, true, false, 0);
            }
        } catch (Throwable e) {
            if ((Build.VERSION.SDK_INT != Build.VERSION_CODES.O && Build.VERSION.SDK_INT != Build.VERSION_CODES.O_MR1)
                    || !Objects.equals(e.getMessage(), "Calling application did not provide package name")) {
                throw e;
            }

            System.err.println("broadcastIntent fails on Android 8.0 or 8.1, fallback to startActivity");
            System.err.flush();

            Intent activityIntent = Intent.createChooser(
                    new Intent("rikka.shizuku.intent.action.REQUEST_BINDER")
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                            .putExtra("data", data),
                    "Request binder from Xhizuku"
            );

            am.startActivityAsUser(null, callingPackage, activityIntent, null, null, null, 0, 0, null, null, Os.getuid() / 100000);
        }
    }

    private static void onBinderReceived(IBinder binder) {
        shizukuBinder = binder;

        try {
            switch (command) {
                case "status":
                    cmdStatus();
                    break;
                case "start":
                    cmdStart();
                    break;
                case "stop":
                    cmdStop();
                    break;
                case "grant":
                    cmdGrant();
                    break;
                case "revoke":
                    cmdRevoke();
                    break;
                case "modules":
                    cmdModules();
                    break;
                default:
                    System.err.println("tapi: Unknown command: " + command);
                    System.err.flush();
                    System.exit(1);
                    break;
            }
        } catch (Throwable e) {
            System.err.println("tapi: Error executing command '" + command + "': " + e.getMessage());
            e.printStackTrace(System.err);
            System.err.flush();
            System.exit(1);
        }

        System.exit(0);
    }

    private static void cmdStatus() {
        boolean serverRunning = binderPing(shizukuBinder);
        int version = getServiceInt(TRANSACTION_getVersion);
        int uid = getServiceInt(TRANSACTION_getUid);

        System.out.println("Xhizuku server status:");
        System.out.println("  Running: " + (serverRunning ? "yes" : "no"));
        System.out.println("  Server version: " + version);
        System.out.println("  Server UID: " + uid);
        System.out.flush();
    }

    private static void cmdStart() {
        System.out.println("tapi: Server start is managed by the Xhizuku starter.");
        System.out.println("tapi: Use the Xhizuku app or starter script to start the server.");
        System.out.flush();
    }

    private static void cmdStop() {
        try {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(BINDER_DESCRIPTOR);
                shizukuBinder.transact(TRANSACTION_exit, data, reply, 0);
                reply.readException();
            } finally {
                data.recycle();
                reply.recycle();
            }
            System.out.println("tapi: Xhizuku server stop requested.");
            System.out.flush();
        } catch (RemoteException e) {
            System.err.println("tapi: Failed to stop server: " + e.getMessage());
            System.err.flush();
            System.exit(1);
        }
    }

    private static void cmdGrant() {
        if (args.length < 1) {
            System.err.println("tapi: Usage: tapi xhizuku grant <package>");
            System.err.flush();
            System.exit(1);
        }

        String packageName = args[0];
        int uid = getUidForPackage(packageName);
        if (uid < 0) {
            System.err.println("tapi: Package not found: " + packageName);
            System.err.flush();
            System.exit(1);
        }

        updateFlagsForUid(uid, MASK_PERMISSION, FLAG_ALLOWED);
        System.out.println("tapi: Granted Shizuku permission to " + packageName + " (uid=" + uid + ")");
        System.out.flush();
    }

    private static void cmdRevoke() {
        if (args.length < 1) {
            System.err.println("tapi: Usage: tapi xhizuku revoke <package>");
            System.err.flush();
            System.exit(1);
        }

        String packageName = args[0];
        int uid = getUidForPackage(packageName);
        if (uid < 0) {
            System.err.println("tapi: Package not found: " + packageName);
            System.err.flush();
            System.exit(1);
        }

        updateFlagsForUid(uid, MASK_PERMISSION, 0);
        System.out.println("tapi: Revoked Shizuku permission from " + packageName + " (uid=" + uid + ")");
        System.out.flush();
    }

    private static void cmdModules() {
        String modulesDir = "/data/data/" + BuildConfig.MANAGER_APPLICATION_ID + "/files/adb_modules";
        String[] cmd = new String[]{"sh", "-c",
                "for d in " + modulesDir + "/*/; do " +
                        "[ -d \"$d\" ] || continue; " +
                        "id=\"$(basename \"$d\")\"; " +
                        "name=\"$id\"; version=\"-\"; " +
                        "if [ -f \"$d/module.prop\" ]; then " +
                        "  name=$(grep '^name=' \"$d/module.prop\" | head -1 | cut -d= -f2-); " +
                        "  version=$(grep '^version=' \"$d/module.prop\" | head -1 | cut -d= -f2-); " +
                        "fi; " +
                        "[ -z \"$name\" ] && name=\"$id\"; " +
                        "[ -z \"$version\" ] && version=\"-\"; " +
                        "if [ -f \"$d/disable\" ]; then " +
                        "  status=\"disabled\"; " +
                        "else " +
                        "  status=\"enabled\"; " +
                        "fi; " +
                        "echo \"$id | $name | v$version | $status\"; " +
                        "done"
        };
        try {
            String output = execRemoteProcess(cmd);
            if (output == null || output.isEmpty()) {
                System.out.println("tapi: No Xhizuku modules installed");
            } else {
                System.out.println("Installed Xhizuku modules:");
                System.out.println(output);
            }
            System.out.flush();
        } catch (Throwable e) {
            System.err.println("tapi: Failed to list modules: " + e.getMessage());
            System.err.flush();
            System.exit(1);
        }
    }

    private static boolean binderPing(IBinder binder) {
        if (binder == null) return false;
        try {
            return binder.pingBinder();
        } catch (Throwable e) {
            return false;
        }
    }

    private static int getServiceInt(int transactionCode) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(BINDER_DESCRIPTOR);
            shizukuBinder.transact(transactionCode, data, reply, 0);
            reply.readException();
            return reply.readInt();
        } catch (Throwable e) {
            return -1;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private static void updateFlagsForUid(int uid, int mask, int value) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(BINDER_DESCRIPTOR);
            data.writeInt(uid);
            data.writeInt(mask);
            data.writeInt(value);
            shizukuBinder.transact(TRANSACTION_updateFlagsForUid, data, reply, 0);
            reply.readException();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private static int getUidForPackage(String packageName) {
        try {
            var appInfo = PackageManagerApis.getApplicationInfoNoThrow(packageName, 0, 0);
            if (appInfo != null) {
                return appInfo.uid;
            }
        } catch (Throwable e) {
        }
        return -1;
    }

    private static String execRemoteProcess(String[] cmd) throws Throwable {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        IBinder remoteProcessBinder = null;
        try {
            data.writeInterfaceToken(BINDER_DESCRIPTOR);
            data.writeStringArray(cmd);
            data.writeStringArray(null);
            data.writeString(null);
            shizukuBinder.transact(TRANSACTION_newProcess, data, reply, 0);
            reply.readException();
            remoteProcessBinder = reply.readStrongBinder();
            if (remoteProcessBinder == null) {
                throw new IllegalStateException("Remote process binder is null");
            }
        } finally {
            data.recycle();
            reply.recycle();
        }

        String output = "";
        Parcel readData = Parcel.obtain();
        Parcel readReply = Parcel.obtain();
        try {
            readData.writeInterfaceToken("moe.shizuku.server.IRemoteProcess");
            remoteProcessBinder.transact(2, readData, readReply, 0);
            readReply.readException();
            ParcelFileDescriptor pfd = readReply.readInt() != 0
                    ? ParcelFileDescriptor.CREATOR.createFromParcel(readReply)
                    : null;
            if (pfd == null) {
                throw new IllegalStateException("InputStream ParcelFileDescriptor is null");
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new ParcelFileDescriptor.AutoCloseInputStream(pfd)))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(line);
                }
                output = sb.toString();
            }
        } finally {
            readData.recycle();
            readReply.recycle();
        }

        Parcel exitData = Parcel.obtain();
        Parcel exitReply = Parcel.obtain();
        try {
            exitData.writeInterfaceToken("moe.shizuku.server.IRemoteProcess");
            remoteProcessBinder.transact(4, exitData, exitReply, 0);
            exitReply.readException();
        } finally {
            exitData.recycle();
            exitReply.recycle();
        }

        return output;
    }

    private static void showHelp() {
        System.out.println("tapi - Termux API bridge for Xhizuku");
        System.out.println();
        System.out.println("Usage: tapi <command> [arguments]");
        System.out.println();
        System.out.println("Management commands:");
        System.out.println("  xhizuku status              Show Xhizuku server status");
        System.out.println("  xhizuku start               Start Xhizuku server (managed by starter)");
        System.out.println("  xhizuku stop                Stop Xhizuku server");
        System.out.println("  xhizuku grant <package>     Grant Shizuku permission to a package");
        System.out.println("  xhizuku revoke <package>    Revoke Shizuku permission from a package");
        System.out.println("  xhizuku modules             List installed Xhizuku modules");
        System.out.println("  --help, -h                    Show this help message");
        System.out.println();
        System.out.println("Shell commands:");
        System.out.println("  <shell args>                  Pass arguments to ShizukuShellLoader");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  tapi xhizuku status");
        System.out.println("  tapi xhizuku grant com.example.app");
        System.out.println("  tapi xhizuku revoke com.example.app");
        System.out.println("  tapi xhizuku modules");
        System.out.println("  tapi -- ls -la");
        System.out.flush();
    }

    private static void abort(String message) {
        System.err.println(message);
        System.err.flush();
        System.exit(1);
    }

    private static java.lang.reflect.Method findBroadcastMethod(Object am) {
        java.lang.reflect.Method best = null;
        for (java.lang.reflect.Method m : am.getClass().getMethods()) {
            if ("broadcastIntentWithFeature".equals(m.getName())) {
                if (best == null || m.getParameterTypes().length > best.getParameterTypes().length) {
                    best = m;
                }
            }
        }
        return best;
    }

    public static void main(String[] rawArgs) {
        if (rawArgs.length == 0) {
            showHelp();
            System.exit(0);
        }

        if ("--help".equals(rawArgs[0]) || "-h".equals(rawArgs[0])) {
            showHelp();
            System.exit(0);
        }

        boolean isXhizukuCommand = false;
        int commandStartIndex = 0;

        if ("xhizuku".equals(rawArgs[0]) || "nightzuku".equals(rawArgs[0])) {
            isXhizukuCommand = true;
            commandStartIndex = 1;
        } else if ("status".equals(rawArgs[0]) || "start".equals(rawArgs[0]) || "stop".equals(rawArgs[0])
                || "grant".equals(rawArgs[0]) || "revoke".equals(rawArgs[0])
                || "modules".equals(rawArgs[0]) || "--modules".equals(rawArgs[0])) {
            isXhizukuCommand = true;
            commandStartIndex = 0;
        }

        if (!isXhizukuCommand) {
            try {
                Class<?> shellLoaderClass = Class.forName("rikka.shizuku.shell.ShizukuShellLoader");
                shellLoaderClass.getDeclaredMethod("main", String[].class).invoke(null, (Object) rawArgs);
            } catch (Throwable e) {
                System.err.println("tapi: Failed to launch shell: " + e.getMessage());
                e.printStackTrace(System.err);
                System.err.flush();
                System.exit(1);
            }
            return;
        }

        if (commandStartIndex >= rawArgs.length) {
            System.err.println("tapi: Missing command after 'xhizuku'");
            System.err.println("tapi: Run 'tapi --help' for usage information.");
            System.err.flush();
            System.exit(1);
        }

        command = rawArgs[commandStartIndex];
        if (command.startsWith("--")) {
            command = command.substring(2);
        }
        if (commandStartIndex + 1 < rawArgs.length) {
            args = new String[rawArgs.length - commandStartIndex - 1];
            System.arraycopy(rawArgs, commandStartIndex + 1, args, 0, args.length);
        } else {
            args = new String[0];
        }

        String packageName;
        var pkg = PackageManagerApis.getPackagesForUidNoThrow(Os.getuid());
        if (pkg.size() == 1) {
            packageName = pkg.get(0);
        } else {
            packageName = System.getenv("TAPI_PACKAGE");
            if (TextUtils.isEmpty(packageName) || "PKG".equals(packageName)) {
                packageName = System.getenv("RISH_APPLICATION_ID");
                if (TextUtils.isEmpty(packageName) || "PKG".equals(packageName)) {
                    abort("TAPI_PACKAGE or RISH_APPLICATION_ID is not set, set this environment variable to the id of current application (package name)");
                }
            }
        }

        callingPackage = packageName;

        if (Looper.getMainLooper() == null) {
            Looper.prepareMainLooper();
        }

        handler = new Handler(Looper.getMainLooper());

        try {
            requestForBinder();
        } catch (Throwable tr) {
            tr.printStackTrace(System.err);
            System.err.flush();
            System.exit(1);
        }

        handler.postDelayed(() -> abort(
                String.format(
                        "Request timeout. The connection between the current app (%1$s) and Xhizuku app may be blocked by your system. " +
                                "Please disable all battery optimization features for both current app (%1$s) and Xhizuku app.",
                        callingPackage)
        ), 5000);

        Looper.loop();
        System.exit(0);
    }
}
