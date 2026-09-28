package rikka.shizuku.server;

public class ServerConstants {

    public static final int MANAGER_APP_NOT_FOUND = 50;

    public static final String PERMISSION = "moe.shizuku.manager.permission.API_V23";
    public static final String MANAGER_APPLICATION_ID = "xeonleon.xhizuku";
    public static final String REQUEST_PERMISSION_ACTION = MANAGER_APPLICATION_ID + ".intent.action.REQUEST_PERMISSION";

    public static final int BINDER_TRANSACTION_getApplications = 10001;
    public static final int BINDER_TRANSACTION_setNightDogEnabled = 10002;
    public static final int BINDER_TRANSACTION_getNightDogEnabled = 10003;

    public static final String TERMUX_PACKAGE_NAME = "com.termux";
    public static final String TAPI_META_DATA = "xeonleon.xhizuku.TAPI_SUPPORT";
    public static final String TAPI_CONTENT_AUTHORITY = "xeonleon.xhizuku.tapi";

    /**
     * Optional extra in the permission confirmation reply Bundle carrying the epoch
     * millis when a temporary grant/deny expires. 0 or absent means permanent
     * (subject to the usual allowed/onetime semantics).
     */
    public static final String REQUEST_PERMISSION_REPLY_EXPIRY = "xeonleon.xhizuku.request-permission-reply-expiry";
}
