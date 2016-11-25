package cyanogenmod.providers;

import android.content.ContentResolver;
import android.content.IContentProvider;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings.NameValueTable;
import android.support.v4.widget.ExploreByTouchHelper;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public final class CMSettings {
    private static final Validator sAlwaysTrueValidator;
    private static final Validator sBooleanValidator;
    private static final Validator sColorValidator;
    private static final Validator sNonNegativeIntegerValidator;
    private static final Validator sUriValidator;

    public interface Validator {
    }

    /* renamed from: cyanogenmod.providers.CMSettings.1 */
    static class C05751 implements Validator {
        C05751() {
        }
    }

    /* renamed from: cyanogenmod.providers.CMSettings.2 */
    static class C05762 implements Validator {
        C05762() {
        }
    }

    /* renamed from: cyanogenmod.providers.CMSettings.3 */
    static class C05773 implements Validator {
        C05773() {
        }
    }

    private static final class DelimitedListValidator implements Validator {
        private final boolean mAllowEmptyList;
        private final String mDelimiter;
        private final ArraySet<String> mValidValueSet;

        public DelimitedListValidator(String[] validValues, String delimiter, boolean allowEmptyList) {
            this.mValidValueSet = new ArraySet(Arrays.asList(validValues));
            this.mDelimiter = delimiter;
            this.mAllowEmptyList = allowEmptyList;
        }
    }

    private static final class DiscreteValueValidator implements Validator {
        private final String[] mValues;

        public DiscreteValueValidator(String[] values) {
            this.mValues = values;
        }
    }

    public static final class Global extends NameValueTable {
        public static final Uri CONTENT_URI;
        public static final String[] LEGACY_GLOBAL_SETTINGS;
        private static final NameValueCache sNameValueCache;

        static {
            CONTENT_URI = Uri.parse("content://cmsettings/global");
            sNameValueCache = new NameValueCache("sys.cm_settings_global_version", CONTENT_URI, "GET_global", "PUT_global");
            LEGACY_GLOBAL_SETTINGS = new String[]{"wake_when_plugged_or_unplugged", "power_notifications_vibrate", "power_notifications_ringtone", "zen_disable_ducking_during_media_playback", "wifi_auto_priority"};
        }

        public static String getStringForUser(ContentResolver resolver, String name, int userId) {
            return sNameValueCache.getStringForUser(resolver, name, userId);
        }
    }

    private static final class InclusiveFloatRangeValidator implements Validator {
        private final float mMax;
        private final float mMin;

        public InclusiveFloatRangeValidator(float min, float max) {
            this.mMin = min;
            this.mMax = max;
        }
    }

    private static final class InclusiveIntegerRangeValidator implements Validator {
        private final int mMax;
        private final int mMin;

        public InclusiveIntegerRangeValidator(int min, int max) {
            this.mMin = min;
            this.mMax = max;
        }
    }

    private static class NameValueCache {
        private static final String[] SELECT_VALUE;
        private final String mCallGetCommand;
        private final String mCallSetCommand;
        private IContentProvider mContentProvider;
        private final Uri mUri;
        private final HashMap<String, String> mValues;
        private long mValuesVersion;
        private final String mVersionSystemProperty;

        static {
            SELECT_VALUE = new String[]{"value"};
        }

        public NameValueCache(String versionSystemProperty, Uri uri, String getCommand, String setCommand) {
            this.mValues = new HashMap();
            this.mValuesVersion = 0;
            this.mContentProvider = null;
            this.mVersionSystemProperty = versionSystemProperty;
            this.mUri = uri;
            this.mCallGetCommand = getCommand;
            this.mCallSetCommand = setCommand;
        }

        private IContentProvider lazyGetProvider(ContentResolver cr) {
            IContentProvider cp;
            synchronized (this) {
                cp = this.mContentProvider;
                if (cp == null) {
                    cp = cr.acquireProvider(this.mUri.getAuthority());
                    this.mContentProvider = cp;
                }
            }
            return cp;
        }

        public boolean putStringForUser(ContentResolver cr, String name, String value, int userId) {
            try {
                Bundle arg = new Bundle();
                arg.putString("value", value);
                arg.putInt("_user", userId);
                lazyGetProvider(cr).call(cr.getPackageName(), this.mCallSetCommand, name, arg);
                return true;
            } catch (RemoteException e) {
                Log.w("CMSettings", "Can't set key " + name + " in " + this.mUri, e);
                return false;
            }
        }

        public String getStringForUser(ContentResolver cr, String name, int userId) {
            String value;
            boolean isSelf = userId == UserHandle.myUserId();
            if (isSelf) {
                long newValuesVersion = SystemProperties.getLong(this.mVersionSystemProperty, 0);
                synchronized (this) {
                    if (this.mValuesVersion != newValuesVersion) {
                        this.mValues.clear();
                        this.mValuesVersion = newValuesVersion;
                    }
                    if (this.mValues.containsKey(name)) {
                        String str = (String) this.mValues.get(name);
                        return str;
                    }
                }
            }
            IContentProvider cp = lazyGetProvider(cr);
            if (this.mCallGetCommand != null) {
                Bundle bundle = null;
                if (!isSelf) {
                    try {
                        Bundle args = new Bundle();
                        try {
                            args.putInt("_user", userId);
                            bundle = args;
                        } catch (RemoteException e) {
                        }
                    } catch (RemoteException e2) {
                    }
                }
                Bundle b = cp.call(cr.getPackageName(), this.mCallGetCommand, name, bundle);
                if (b != null) {
                    value = b.getPairValue();
                    if (isSelf) {
                        synchronized (this) {
                            this.mValues.put(name, value);
                        }
                    }
                    return value;
                }
            }
            Cursor cursor = null;
            try {
                cursor = cp.query(cr.getPackageName(), this.mUri, SELECT_VALUE, "name=?", new String[]{name}, null, null);
                if (cursor == null) {
                    Log.w("CMSettings", "Can't get key " + name + " from " + this.mUri);
                    if (cursor != null) {
                        cursor.close();
                    }
                    return null;
                }
                value = cursor.moveToNext() ? cursor.getString(0) : null;
                synchronized (this) {
                    this.mValues.put(name, value);
                }
                if (cursor != null) {
                    cursor.close();
                }
                return value;
            } catch (Throwable e3) {
                try {
                    Log.w("CMSettings", "Can't get key " + name + " from " + this.mUri, e3);
                    return null;
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
            }
        }
    }

    public static final class Secure extends NameValueTable {
        public static final Uri CONTENT_URI;
        public static final String[] LEGACY_SECURE_SETTINGS;
        protected static final ArraySet<String> MOVED_TO_GLOBAL;
        public static final String[] NAVIGATION_RING_TARGETS;
        public static final Validator PROTECTED_COMPONENTS_MANAGER_VALIDATOR;
        public static final Validator PROTECTED_COMPONENTS_VALIDATOR;
        public static final Map<String, Validator> VALIDATORS;
        private static final NameValueCache sNameValueCache;

        /* renamed from: cyanogenmod.providers.CMSettings.Secure.1 */
        static class C05781 implements Validator {
            private final String mDelimiter;

            C05781() {
                this.mDelimiter = "|";
            }
        }

        /* renamed from: cyanogenmod.providers.CMSettings.Secure.2 */
        static class C05792 implements Validator {
            private final String mDelimiter;

            C05792() {
                this.mDelimiter = "|";
            }
        }

        static {
            CONTENT_URI = Uri.parse("content://cmsettings/secure");
            sNameValueCache = new NameValueCache("sys.cm_settings_secure_version", CONTENT_URI, "GET_secure", "PUT_secure");
            MOVED_TO_GLOBAL = new ArraySet(1);
            MOVED_TO_GLOBAL.add("dev_force_show_navbar");
            NAVIGATION_RING_TARGETS = new String[]{"navigation_ring_targets_0", "navigation_ring_targets_1", "navigation_ring_targets_2"};
            LEGACY_SECURE_SETTINGS = new String[]{"advanced_mode", "button_backlight_timeout", "button_brightness", "default_theme_components", "default_theme_package", "dev_force_show_navbar", "keyboard_brightness", "power_menu_actions", "stats_collection", "qs_show_brightness_slider", "sysui_qs_tiles", "sysui_qs_main_tiles", NAVIGATION_RING_TARGETS[0], NAVIGATION_RING_TARGETS[1], NAVIGATION_RING_TARGETS[2], "recents_long_press_activity", "adb_notify", "adb_port", "device_hostname", "kill_app_longpress_back", "protected_components", "live_display_color_matrix", "advanced_reboot", "theme_prev_boot_api_level", "lockscreen_target_actions", "ring_home_button_behavior", "privacy_guard_default", "privacy_guard_notification", "development_shortcut", "performance_profile", "app_perf_profiles_enabled", "qs_location_advanced", "lockscreen_visualizer", "lock_screen_pass_to_security_view"};
            PROTECTED_COMPONENTS_VALIDATOR = new C05781();
            PROTECTED_COMPONENTS_MANAGER_VALIDATOR = new C05792();
            VALIDATORS = new ArrayMap();
            VALIDATORS.put("protected_components", PROTECTED_COMPONENTS_VALIDATOR);
            VALIDATORS.put("protected_component_managers", PROTECTED_COMPONENTS_MANAGER_VALIDATOR);
        }

        public static String getStringForUser(ContentResolver resolver, String name, int userId) {
            if (!MOVED_TO_GLOBAL.contains(name)) {
                return sNameValueCache.getStringForUser(resolver, name, userId);
            }
            Log.w("CMSettings", "Setting " + name + " has moved from CMSettings.Secure" + " to CMSettings.Global, value is unchanged.");
            return Global.getStringForUser(resolver, name, userId);
        }

        public static int getInt(ContentResolver cr, String name, int def) {
            return getIntForUser(cr, name, def, UserHandle.myUserId());
        }

        public static int getIntForUser(ContentResolver cr, String name, int def, int userId) {
            String v = getStringForUser(cr, name, userId);
            if (v != null) {
                try {
                    def = Integer.parseInt(v);
                } catch (NumberFormatException e) {
                    return def;
                }
            }
            return def;
        }
    }

    public static final class System extends NameValueTable {
        public static final Validator APP_SWITCH_WAKE_SCREEN_VALIDATOR;
        public static final Validator ASSIST_WAKE_SCREEN_VALIDATOR;
        public static final Validator BACK_WAKE_SCREEN_VALIDATOR;
        public static final Validator BATTERY_LIGHT_ENABLED_VALIDATOR;
        public static final Validator BATTERY_LIGHT_FULL_COLOR_VALIDATOR;
        public static final Validator BATTERY_LIGHT_LOW_COLOR_VALIDATOR;
        public static final Validator BATTERY_LIGHT_MEDIUM_COLOR_VALIDATOR;
        public static final Validator BATTERY_LIGHT_PULSE_VALIDATOR;
        public static final Validator BLUETOOTH_ACCEPT_ALL_FILES_VALIDATOR;
        public static final Validator CALL_RECORDING_FORMAT_VALIDATOR;
        public static final Validator CAMERA_LAUNCH_VALIDATOR;
        public static final Validator CAMERA_SLEEP_ON_RELEASE_VALIDATOR;
        public static final Validator CAMERA_WAKE_SCREEN_VALIDATOR;
        public static final Uri CONTENT_URI;
        public static final Validator DIALER_OPENCNAM_ACCOUNT_SID_VALIDATOR;
        public static final Validator DIALER_OPENCNAM_AUTH_TOKEN_VALIDATOR;
        public static final Validator DISPLAY_AUTO_CONTRAST_VALIDATOR;
        public static final Validator DISPLAY_AUTO_OUTDOOR_MODE_VALIDATOR;
        public static final Validator DISPLAY_CABC_VALIDATOR;
        public static final Validator DISPLAY_COLOR_ADJUSTMENT_VALIDATOR;
        public static final Validator DISPLAY_COLOR_ENHANCE_VALIDATOR;
        public static final Validator DISPLAY_TEMPERATURE_DAY_VALIDATOR;
        public static final Validator DISPLAY_TEMPERATURE_MODE_VALIDATOR;
        public static final Validator DISPLAY_TEMPERATURE_NIGHT_VALIDATOR;
        public static final Validator DOUBLE_TAP_SLEEP_GESTURE_VALIDATOR;
        public static final Validator ENABLE_FORWARD_LOOKUP_VALIDATOR;
        public static final Validator ENABLE_MWI_NOTIFICATION_VALIDATOR;
        public static final Validator ENABLE_PEOPLE_LOOKUP_VALIDATOR;
        public static final Validator ENABLE_REVERSE_LOOKUP_VALIDATOR;
        public static final Validator FORWARD_LOOKUP_PROVIDER_VALIDATOR;
        public static final Validator HEADSET_CONNECT_PLAYER_VALIDATOR;
        public static final Validator HEADS_UP_BLACKLIST_VALUES_VALIDATOR;
        public static final Validator HEADS_UP_CUSTOM_VALUES_VALIDATOR;
        public static final Validator HEADS_UP_WHITELIST_VALUES_VALIDATOR;
        public static final Validator HIGH_TOUCH_SENSITIVITY_ENABLE_VALIDATOR;
        public static final Validator HOME_WAKE_SCREEN_VALIDATOR;
        public static final Validator INCREASING_RING_RAMP_UP_TIME_VALIDATOR;
        public static final Validator INCREASING_RING_START_VOLUME_VALIDATOR;
        public static final Validator INCREASING_RING_VALIDATOR;
        public static final Validator KEY_APP_SWITCH_ACTION_VALIDATOR;
        public static final Validator KEY_APP_SWITCH_LONG_PRESS_ACTION_VALIDATOR;
        public static final Validator KEY_ASSIST_ACTION_VALIDATOR;
        public static final Validator KEY_ASSIST_LONG_PRESS_ACTION_VALIDATOR;
        public static final Validator KEY_HOME_DOUBLE_TAP_ACTION_VALIDATOR;
        public static final Validator KEY_HOME_LONG_PRESS_ACTION_VALIDATOR;
        public static final Validator KEY_MENU_ACTION_VALIDATOR;
        public static final Validator KEY_MENU_LONG_PRESS_ACTION_VALIDATOR;
        public static final String[] LEGACY_SYSTEM_SETTINGS;
        public static final Validator LIVE_DISPLAY_HINTED_VALIDATOR;
        public static final Validator LOCKSCREEN_PIN_SCRAMBLE_LAYOUT_VALIDATOR;
        public static final Validator LOCKSCREEN_ROTATION_VALIDATOR;
        public static final Validator MENU_WAKE_SCREENN_VALIDATOR;
        protected static final ArraySet<String> MOVED_TO_SECURE;
        public static final Validator NAVBAR_LEFT_IN_LANDSCAPE_VALIDATOR;
        public static final Validator NAVIGATION_BAR_MENU_ARROW_KEYS_VALIDATOR;
        public static final Validator NAV_BUTTONS_VALIDATOR;
        public static final Validator NOTIFICATION_LIGHT_BRIGHTNESS_LEVEL_VALIDATOR;
        public static final Validator NOTIFICATION_LIGHT_COLOR_AUTO_VALIDATOR;
        public static final Validator NOTIFICATION_LIGHT_MULTIPLE_LEDS_ENABLE_VALIDATOR;
        public static final Validator NOTIFICATION_LIGHT_PULSE_CALL_COLOR_VALIDATOR;
        public static final Validator NOTIFICATION_LIGHT_PULSE_CALL_LED_OFF_VALIDATOR;
        public static final Validator NOTIFICATION_LIGHT_PULSE_CALL_LED_ON_VALIDATOR;
        public static final Validator NOTIFICATION_LIGHT_PULSE_CUSTOM_ENABLE_VALIDATOR;
        public static final Validator NOTIFICATION_LIGHT_PULSE_CUSTOM_VALUES_VALIDATOR;
        public static final Validator NOTIFICATION_LIGHT_PULSE_DEFAULT_COLOR_VALIDATOR;
        public static final Validator NOTIFICATION_LIGHT_PULSE_DEFAULT_LED_OFF_VALIDATOR;
        public static final Validator NOTIFICATION_LIGHT_PULSE_DEFAULT_LED_ON_VALIDATOR;
        public static final Validator NOTIFICATION_LIGHT_PULSE_VMAIL_COLOR_VALIDATOR;
        public static final Validator NOTIFICATION_LIGHT_PULSE_VMAIL_LED_OFF_VALIDATOR;
        public static final Validator NOTIFICATION_LIGHT_PULSE_VMAIL_LED_ON_VALIDATOR;
        public static final Validator NOTIFICATION_LIGHT_SCREEN_ON_VALIDATOR;
        public static final Validator NOTIFICATION_PLAY_QUEUE_VALIDATOR;
        public static final Validator PEOPLE_LOOKUP_PROVIDER_VALIDATOR;
        public static final Validator PROXIMITY_ON_WAKE_VALIDATOR;
        public static final Validator QS_SHOW_BRIGHTNESS_SLIDER_VALIDATOR;
        public static final Validator RECENTS_SHOW_SEARCH_BAR_VALIDATOR;
        public static final Validator REVERSE_LOOKUP_PROVIDER_VALIDATOR;
        public static final Validator SAFE_HEADSET_VOLUME_VALIDATOR;
        public static final Validator SHOW_ALARM_ICON_VALIDATOR;
        public static final Validator SHOW_NEXT_ALARM_VALIDATOR;
        public static final Validator STATUS_BAR_AM_PM_VALIDATOR;
        public static final Validator STATUS_BAR_BATTERY_STYLE_VALIDATOR;
        public static final Validator STATUS_BAR_BRIGHTNESS_CONTROL_VALIDATOR;
        public static final Validator STATUS_BAR_CLOCK_VALIDATOR;
        public static final Validator STATUS_BAR_IME_SWITCHER_VALIDATOR;
        public static final Validator STATUS_BAR_NOTIF_COUNT_VALIDATOR;
        public static final Validator STATUS_BAR_QUICK_QS_PULLDOWN_VALIDATOR;
        public static final Validator STATUS_BAR_SHOW_BATTERY_PERCENT_VALIDATOR;
        public static final Validator STATUS_BAR_SHOW_WEATHER_VALIDATOR;
        public static final Validator STREAM_VOLUME_STEPS_CHANGED_VALIDATOR;
        public static final Validator SWAP_VOLUME_KEYS_ON_ROTATION_VALIDATOR;
        public static final Validator SYSTEM_PROFILES_ENABLED_VALIDATOR;
        public static final Validator T9_SEARCH_INPUT_LOCALE_VALIDATOR;
        public static final Validator TOUCHSCREEN_GESTURE_HAPTIC_FEEDBACK_VALIDATOR;
        public static final Validator USE_EDGE_SERVICE_FOR_GESTURES_VALIDATOR;
        public static final Map<String, Validator> VALIDATORS;
        public static final Validator VOLBTN_MUSIC_CONTROLS_VALIDATOR;
        public static final Validator VOLUME_ADJUST_SOUNDS_ENABLED_VALIDATOR;
        public static final Validator VOLUME_KEYS_CONTROL_RING_STREAM_VALIDATOR;
        public static final Validator VOLUME_WAKE_SCREEN_VALIDATOR;
        public static final Validator ZEN_ALLOW_LIGHTS_VALIDATOR;
        public static final Validator ZEN_PRIORITY_ALLOW_LIGHTS_VALIDATOR;
        public static final Validator __MAGICAL_TEST_PASSING_ENABLER_VALIDATOR;
        private static final NameValueCache sNameValueCache;

        /* renamed from: cyanogenmod.providers.CMSettings.System.1 */
        static class C05801 implements Validator {
            C05801() {
            }
        }

        /* renamed from: cyanogenmod.providers.CMSettings.System.2 */
        static class C05812 implements Validator {
            C05812() {
            }
        }

        /* renamed from: cyanogenmod.providers.CMSettings.System.3 */
        static class C05823 implements Validator {
            C05823() {
            }
        }

        static {
            CONTENT_URI = Uri.parse("content://cmsettings/system");
            sNameValueCache = new NameValueCache("sys.cm_settings_system_version", CONTENT_URI, "GET_system", "PUT_system");
            MOVED_TO_SECURE = new ArraySet(1);
            MOVED_TO_SECURE.add("dev_force_show_navbar");
            NOTIFICATION_PLAY_QUEUE_VALIDATOR = CMSettings.sBooleanValidator;
            HIGH_TOUCH_SENSITIVITY_ENABLE_VALIDATOR = CMSettings.sBooleanValidator;
            SYSTEM_PROFILES_ENABLED_VALIDATOR = CMSettings.sBooleanValidator;
            STATUS_BAR_CLOCK_VALIDATOR = new InclusiveIntegerRangeValidator(0, 3);
            ZEN_ALLOW_LIGHTS_VALIDATOR = CMSettings.sBooleanValidator;
            ZEN_PRIORITY_ALLOW_LIGHTS_VALIDATOR = CMSettings.sBooleanValidator;
            STATUS_BAR_AM_PM_VALIDATOR = new InclusiveIntegerRangeValidator(0, 2);
            STATUS_BAR_BATTERY_STYLE_VALIDATOR = new DiscreteValueValidator(new String[]{"0", "2", "3", "4", "5", "6"});
            STATUS_BAR_SHOW_BATTERY_PERCENT_VALIDATOR = new InclusiveIntegerRangeValidator(0, 2);
            INCREASING_RING_VALIDATOR = CMSettings.sBooleanValidator;
            INCREASING_RING_START_VOLUME_VALIDATOR = new InclusiveFloatRangeValidator(0.0f, 1.0f);
            INCREASING_RING_RAMP_UP_TIME_VALIDATOR = new InclusiveIntegerRangeValidator(5, 60);
            VOLUME_ADJUST_SOUNDS_ENABLED_VALIDATOR = CMSettings.sBooleanValidator;
            NAV_BUTTONS_VALIDATOR = new DelimitedListValidator(new String[]{"empty", "home", "back", "search", "recent", "menu0", "menu1", "menu2", "dpad_left", "dpad_right", "power", "notifications", "torch", "camera", "screenshot", "expand", "app_picker"}, "|", true);
            VOLUME_KEYS_CONTROL_RING_STREAM_VALIDATOR = CMSettings.sBooleanValidator;
            NAVIGATION_BAR_MENU_ARROW_KEYS_VALIDATOR = CMSettings.sBooleanValidator;
            KEY_HOME_LONG_PRESS_ACTION_VALIDATOR = new InclusiveIntegerRangeValidator(0, 8);
            KEY_HOME_DOUBLE_TAP_ACTION_VALIDATOR = new InclusiveIntegerRangeValidator(0, 8);
            BACK_WAKE_SCREEN_VALIDATOR = CMSettings.sBooleanValidator;
            MENU_WAKE_SCREENN_VALIDATOR = CMSettings.sBooleanValidator;
            VOLUME_WAKE_SCREEN_VALIDATOR = CMSettings.sBooleanValidator;
            KEY_MENU_ACTION_VALIDATOR = new InclusiveIntegerRangeValidator(0, 8);
            KEY_MENU_LONG_PRESS_ACTION_VALIDATOR = new InclusiveIntegerRangeValidator(0, 8);
            KEY_ASSIST_ACTION_VALIDATOR = new InclusiveIntegerRangeValidator(0, 8);
            KEY_ASSIST_LONG_PRESS_ACTION_VALIDATOR = new InclusiveIntegerRangeValidator(0, 8);
            KEY_APP_SWITCH_ACTION_VALIDATOR = new InclusiveIntegerRangeValidator(0, 8);
            KEY_APP_SWITCH_LONG_PRESS_ACTION_VALIDATOR = new InclusiveIntegerRangeValidator(0, 8);
            HOME_WAKE_SCREEN_VALIDATOR = CMSettings.sBooleanValidator;
            ASSIST_WAKE_SCREEN_VALIDATOR = CMSettings.sBooleanValidator;
            APP_SWITCH_WAKE_SCREEN_VALIDATOR = CMSettings.sBooleanValidator;
            CAMERA_WAKE_SCREEN_VALIDATOR = CMSettings.sBooleanValidator;
            CAMERA_SLEEP_ON_RELEASE_VALIDATOR = CMSettings.sBooleanValidator;
            CAMERA_LAUNCH_VALIDATOR = CMSettings.sBooleanValidator;
            SWAP_VOLUME_KEYS_ON_ROTATION_VALIDATOR = new InclusiveIntegerRangeValidator(0, 2);
            BATTERY_LIGHT_ENABLED_VALIDATOR = CMSettings.sBooleanValidator;
            BATTERY_LIGHT_PULSE_VALIDATOR = CMSettings.sBooleanValidator;
            BATTERY_LIGHT_LOW_COLOR_VALIDATOR = CMSettings.sColorValidator;
            BATTERY_LIGHT_MEDIUM_COLOR_VALIDATOR = CMSettings.sColorValidator;
            BATTERY_LIGHT_FULL_COLOR_VALIDATOR = CMSettings.sColorValidator;
            ENABLE_MWI_NOTIFICATION_VALIDATOR = CMSettings.sBooleanValidator;
            PROXIMITY_ON_WAKE_VALIDATOR = CMSettings.sBooleanValidator;
            ENABLE_FORWARD_LOOKUP_VALIDATOR = CMSettings.sBooleanValidator;
            ENABLE_PEOPLE_LOOKUP_VALIDATOR = CMSettings.sBooleanValidator;
            ENABLE_REVERSE_LOOKUP_VALIDATOR = CMSettings.sBooleanValidator;
            FORWARD_LOOKUP_PROVIDER_VALIDATOR = CMSettings.sAlwaysTrueValidator;
            PEOPLE_LOOKUP_PROVIDER_VALIDATOR = CMSettings.sAlwaysTrueValidator;
            REVERSE_LOOKUP_PROVIDER_VALIDATOR = CMSettings.sAlwaysTrueValidator;
            DIALER_OPENCNAM_ACCOUNT_SID_VALIDATOR = CMSettings.sAlwaysTrueValidator;
            DIALER_OPENCNAM_AUTH_TOKEN_VALIDATOR = CMSettings.sAlwaysTrueValidator;
            DISPLAY_TEMPERATURE_DAY_VALIDATOR = new InclusiveIntegerRangeValidator(1000, 10000);
            DISPLAY_TEMPERATURE_NIGHT_VALIDATOR = new InclusiveIntegerRangeValidator(1000, 10000);
            DISPLAY_TEMPERATURE_MODE_VALIDATOR = new InclusiveIntegerRangeValidator(0, 4);
            DISPLAY_AUTO_OUTDOOR_MODE_VALIDATOR = CMSettings.sBooleanValidator;
            DISPLAY_CABC_VALIDATOR = CMSettings.sBooleanValidator;
            DISPLAY_COLOR_ENHANCE_VALIDATOR = CMSettings.sBooleanValidator;
            DISPLAY_AUTO_CONTRAST_VALIDATOR = CMSettings.sBooleanValidator;
            DISPLAY_COLOR_ADJUSTMENT_VALIDATOR = new C05801();
            LIVE_DISPLAY_HINTED_VALIDATOR = new InclusiveIntegerRangeValidator(-3, 1);
            DOUBLE_TAP_SLEEP_GESTURE_VALIDATOR = CMSettings.sBooleanValidator;
            STATUS_BAR_SHOW_WEATHER_VALIDATOR = CMSettings.sBooleanValidator;
            RECENTS_SHOW_SEARCH_BAR_VALIDATOR = CMSettings.sBooleanValidator;
            NAVBAR_LEFT_IN_LANDSCAPE_VALIDATOR = CMSettings.sBooleanValidator;
            T9_SEARCH_INPUT_LOCALE_VALIDATOR = new C05812();
            BLUETOOTH_ACCEPT_ALL_FILES_VALIDATOR = CMSettings.sBooleanValidator;
            LOCKSCREEN_PIN_SCRAMBLE_LAYOUT_VALIDATOR = CMSettings.sBooleanValidator;
            LOCKSCREEN_ROTATION_VALIDATOR = CMSettings.sBooleanValidator;
            SHOW_ALARM_ICON_VALIDATOR = CMSettings.sBooleanValidator;
            SHOW_NEXT_ALARM_VALIDATOR = CMSettings.sBooleanValidator;
            SAFE_HEADSET_VOLUME_VALIDATOR = CMSettings.sBooleanValidator;
            STREAM_VOLUME_STEPS_CHANGED_VALIDATOR = CMSettings.sBooleanValidator;
            STATUS_BAR_IME_SWITCHER_VALIDATOR = CMSettings.sBooleanValidator;
            STATUS_BAR_QUICK_QS_PULLDOWN_VALIDATOR = new InclusiveIntegerRangeValidator(0, 2);
            QS_SHOW_BRIGHTNESS_SLIDER_VALIDATOR = CMSettings.sBooleanValidator;
            STATUS_BAR_BRIGHTNESS_CONTROL_VALIDATOR = CMSettings.sBooleanValidator;
            VOLBTN_MUSIC_CONTROLS_VALIDATOR = CMSettings.sBooleanValidator;
            USE_EDGE_SERVICE_FOR_GESTURES_VALIDATOR = CMSettings.sBooleanValidator;
            STATUS_BAR_NOTIF_COUNT_VALIDATOR = CMSettings.sBooleanValidator;
            CALL_RECORDING_FORMAT_VALIDATOR = new InclusiveIntegerRangeValidator(0, 1);
            NOTIFICATION_LIGHT_BRIGHTNESS_LEVEL_VALIDATOR = new InclusiveIntegerRangeValidator(1, 255);
            NOTIFICATION_LIGHT_MULTIPLE_LEDS_ENABLE_VALIDATOR = CMSettings.sBooleanValidator;
            NOTIFICATION_LIGHT_SCREEN_ON_VALIDATOR = CMSettings.sBooleanValidator;
            NOTIFICATION_LIGHT_PULSE_DEFAULT_COLOR_VALIDATOR = CMSettings.sColorValidator;
            NOTIFICATION_LIGHT_PULSE_DEFAULT_LED_ON_VALIDATOR = CMSettings.sNonNegativeIntegerValidator;
            NOTIFICATION_LIGHT_PULSE_DEFAULT_LED_OFF_VALIDATOR = CMSettings.sNonNegativeIntegerValidator;
            NOTIFICATION_LIGHT_PULSE_CALL_COLOR_VALIDATOR = CMSettings.sColorValidator;
            NOTIFICATION_LIGHT_PULSE_CALL_LED_ON_VALIDATOR = CMSettings.sNonNegativeIntegerValidator;
            NOTIFICATION_LIGHT_PULSE_CALL_LED_OFF_VALIDATOR = CMSettings.sNonNegativeIntegerValidator;
            NOTIFICATION_LIGHT_PULSE_VMAIL_COLOR_VALIDATOR = CMSettings.sColorValidator;
            NOTIFICATION_LIGHT_PULSE_VMAIL_LED_ON_VALIDATOR = CMSettings.sNonNegativeIntegerValidator;
            NOTIFICATION_LIGHT_PULSE_VMAIL_LED_OFF_VALIDATOR = CMSettings.sNonNegativeIntegerValidator;
            NOTIFICATION_LIGHT_PULSE_CUSTOM_ENABLE_VALIDATOR = CMSettings.sBooleanValidator;
            HEADS_UP_CUSTOM_VALUES_VALIDATOR = CMSettings.sAlwaysTrueValidator;
            HEADS_UP_BLACKLIST_VALUES_VALIDATOR = CMSettings.sAlwaysTrueValidator;
            HEADS_UP_WHITELIST_VALUES_VALIDATOR = CMSettings.sAlwaysTrueValidator;
            NOTIFICATION_LIGHT_PULSE_CUSTOM_VALUES_VALIDATOR = new C05823();
            NOTIFICATION_LIGHT_COLOR_AUTO_VALIDATOR = CMSettings.sBooleanValidator;
            HEADSET_CONNECT_PLAYER_VALIDATOR = CMSettings.sBooleanValidator;
            TOUCHSCREEN_GESTURE_HAPTIC_FEEDBACK_VALIDATOR = CMSettings.sBooleanValidator;
            __MAGICAL_TEST_PASSING_ENABLER_VALIDATOR = CMSettings.sAlwaysTrueValidator;
            LEGACY_SYSTEM_SETTINGS = new String[]{"nav_buttons", "key_home_long_press_action", "key_home_double_tap_action", "back_wake_screen", "menu_wake_screen", "volume_wake_screen", "key_menu_action", "key_menu_long_press_action", "key_assist_action", "key_assist_long_press_action", "key_app_switch_action", "key_app_switch_long_press_action", "home_wake_screen", "assist_wake_screen", "app_switch_wake_screen", "camera_wake_screen", "camera_sleep_on_release", "camera_launch", "swap_volume_keys_on_rotation", "battery_light_enabled", "battery_light_pulse", "battery_light_low_color", "battery_light_medium_color", "battery_light_full_color", "enable_mwi_notification", "proximity_on_wake", "enable_forward_lookup", "enable_people_lookup", "enable_reverse_lookup", "forward_lookup_provider", "people_lookup_provider", "reverse_lookup_provider", "dialer_opencnam_account_sid", "dialer_opencnam_auth_token", "display_temperature_day", "display_temperature_night", "display_temperature_mode", "display_auto_outdoor_mode", "display_low_power", "display_color_enhance", "display_color_adjustment", "live_display_hinted", "double_tap_sleep_gesture", "status_bar_show_weather", "recents_show_search_bar", "navigation_bar_left", "t9_search_input_locale", "bluetooth_accept_all_files", "lockscreen_scramble_pin_layout", "show_alarm_icon", "show_next_alarm", "safe_headset_volume", "stream_volume_steps_changed", "status_bar_ime_switcher", "qs_show_brightness_slider", "status_bar_brightness_control", "volbtn_music_controls", "swap_volume_keys_on_rotation", "edge_service_for_gestures", "status_bar_notif_count", "call_recording_format", "notification_light_brightness_level", "notification_light_multiple_leds_enable", "notification_light_screen_on_enable", "notification_light_pulse_default_color", "notification_light_pulse_default_led_on", "notification_light_pulse_default_led_off", "notification_light_pulse_call_color", "notification_light_pulse_call_led_on", "notification_light_pulse_call_led_off", "notification_light_pulse_vmail_color", "notification_light_pulse_vmail_led_on", "notification_light_pulse_vmail_led_off", "notification_light_pulse_custom_enable", "notification_light_pulse_custom_values", "qs_quick_pulldown", "volume_adjust_sounds_enabled", "system_profiles_enabled", "increasing_ring", "increasing_ring_start_vol", "increasing_ring_ramp_up_time", "status_bar_clock", "status_bar_am_pm", "status_bar_battery_style", "status_bar_show_battery_percent", "volume_keys_control_ring_stream", "navigation_bar_menu_arrow_keys", "headset_connect_player", "allow_lights", "touchscreen_gesture_haptic_feedback"};
            VALIDATORS = new ArrayMap();
            VALIDATORS.put("notification_play_queue", NOTIFICATION_PLAY_QUEUE_VALIDATOR);
            VALIDATORS.put("high_touch_sensitivity_enable", HIGH_TOUCH_SENSITIVITY_ENABLE_VALIDATOR);
            VALIDATORS.put("system_profiles_enabled", SYSTEM_PROFILES_ENABLED_VALIDATOR);
            VALIDATORS.put("status_bar_clock", STATUS_BAR_CLOCK_VALIDATOR);
            VALIDATORS.put("status_bar_am_pm", STATUS_BAR_AM_PM_VALIDATOR);
            VALIDATORS.put("status_bar_battery_style", STATUS_BAR_BATTERY_STYLE_VALIDATOR);
            VALIDATORS.put("status_bar_show_battery_percent", STATUS_BAR_SHOW_BATTERY_PERCENT_VALIDATOR);
            VALIDATORS.put("increasing_ring", INCREASING_RING_VALIDATOR);
            VALIDATORS.put("increasing_ring_start_vol", INCREASING_RING_START_VOLUME_VALIDATOR);
            VALIDATORS.put("increasing_ring_ramp_up_time", INCREASING_RING_RAMP_UP_TIME_VALIDATOR);
            VALIDATORS.put("volume_adjust_sounds_enabled", VOLUME_ADJUST_SOUNDS_ENABLED_VALIDATOR);
            VALIDATORS.put("nav_buttons", NAV_BUTTONS_VALIDATOR);
            VALIDATORS.put("volume_keys_control_ring_stream", VOLUME_KEYS_CONTROL_RING_STREAM_VALIDATOR);
            VALIDATORS.put("navigation_bar_menu_arrow_keys", NAVIGATION_BAR_MENU_ARROW_KEYS_VALIDATOR);
            VALIDATORS.put("key_home_long_press_action", KEY_HOME_LONG_PRESS_ACTION_VALIDATOR);
            VALIDATORS.put("key_home_double_tap_action", KEY_HOME_DOUBLE_TAP_ACTION_VALIDATOR);
            VALIDATORS.put("back_wake_screen", BACK_WAKE_SCREEN_VALIDATOR);
            VALIDATORS.put("menu_wake_screen", MENU_WAKE_SCREENN_VALIDATOR);
            VALIDATORS.put("volume_wake_screen", VOLUME_WAKE_SCREEN_VALIDATOR);
            VALIDATORS.put("key_menu_action", KEY_MENU_ACTION_VALIDATOR);
            VALIDATORS.put("key_menu_long_press_action", KEY_MENU_LONG_PRESS_ACTION_VALIDATOR);
            VALIDATORS.put("key_assist_action", KEY_ASSIST_ACTION_VALIDATOR);
            VALIDATORS.put("key_assist_long_press_action", KEY_ASSIST_LONG_PRESS_ACTION_VALIDATOR);
            VALIDATORS.put("key_app_switch_action", KEY_APP_SWITCH_ACTION_VALIDATOR);
            VALIDATORS.put("key_app_switch_long_press_action", KEY_APP_SWITCH_LONG_PRESS_ACTION_VALIDATOR);
            VALIDATORS.put("home_wake_screen", HOME_WAKE_SCREEN_VALIDATOR);
            VALIDATORS.put("assist_wake_screen", ASSIST_WAKE_SCREEN_VALIDATOR);
            VALIDATORS.put("app_switch_wake_screen", APP_SWITCH_WAKE_SCREEN_VALIDATOR);
            VALIDATORS.put("camera_wake_screen", CAMERA_WAKE_SCREEN_VALIDATOR);
            VALIDATORS.put("camera_sleep_on_release", CAMERA_SLEEP_ON_RELEASE_VALIDATOR);
            VALIDATORS.put("camera_launch", CAMERA_LAUNCH_VALIDATOR);
            VALIDATORS.put("swap_volume_keys_on_rotation", SWAP_VOLUME_KEYS_ON_ROTATION_VALIDATOR);
            VALIDATORS.put("battery_light_enabled", BATTERY_LIGHT_ENABLED_VALIDATOR);
            VALIDATORS.put("battery_light_pulse", BATTERY_LIGHT_PULSE_VALIDATOR);
            VALIDATORS.put("battery_light_low_color", BATTERY_LIGHT_LOW_COLOR_VALIDATOR);
            VALIDATORS.put("battery_light_medium_color", BATTERY_LIGHT_MEDIUM_COLOR_VALIDATOR);
            VALIDATORS.put("battery_light_full_color", BATTERY_LIGHT_FULL_COLOR_VALIDATOR);
            VALIDATORS.put("enable_mwi_notification", ENABLE_MWI_NOTIFICATION_VALIDATOR);
            VALIDATORS.put("proximity_on_wake", PROXIMITY_ON_WAKE_VALIDATOR);
            VALIDATORS.put("enable_forward_lookup", ENABLE_FORWARD_LOOKUP_VALIDATOR);
            VALIDATORS.put("enable_people_lookup", ENABLE_PEOPLE_LOOKUP_VALIDATOR);
            VALIDATORS.put("enable_reverse_lookup", ENABLE_REVERSE_LOOKUP_VALIDATOR);
            VALIDATORS.put("forward_lookup_provider", FORWARD_LOOKUP_PROVIDER_VALIDATOR);
            VALIDATORS.put("people_lookup_provider", PEOPLE_LOOKUP_PROVIDER_VALIDATOR);
            VALIDATORS.put("reverse_lookup_provider", REVERSE_LOOKUP_PROVIDER_VALIDATOR);
            VALIDATORS.put("dialer_opencnam_account_sid", DIALER_OPENCNAM_ACCOUNT_SID_VALIDATOR);
            VALIDATORS.put("dialer_opencnam_auth_token", DIALER_OPENCNAM_AUTH_TOKEN_VALIDATOR);
            VALIDATORS.put("display_temperature_day", DISPLAY_TEMPERATURE_DAY_VALIDATOR);
            VALIDATORS.put("display_temperature_night", DISPLAY_TEMPERATURE_NIGHT_VALIDATOR);
            VALIDATORS.put("display_temperature_mode", DISPLAY_TEMPERATURE_MODE_VALIDATOR);
            VALIDATORS.put("display_auto_contrast", DISPLAY_AUTO_CONTRAST_VALIDATOR);
            VALIDATORS.put("display_auto_outdoor_mode", DISPLAY_AUTO_OUTDOOR_MODE_VALIDATOR);
            VALIDATORS.put("display_low_power", DISPLAY_CABC_VALIDATOR);
            VALIDATORS.put("display_color_enhance", DISPLAY_COLOR_ENHANCE_VALIDATOR);
            VALIDATORS.put("display_color_adjustment", DISPLAY_COLOR_ADJUSTMENT_VALIDATOR);
            VALIDATORS.put("live_display_hinted", LIVE_DISPLAY_HINTED_VALIDATOR);
            VALIDATORS.put("double_tap_sleep_gesture", DOUBLE_TAP_SLEEP_GESTURE_VALIDATOR);
            VALIDATORS.put("status_bar_show_weather", STATUS_BAR_SHOW_WEATHER_VALIDATOR);
            VALIDATORS.put("recents_show_search_bar", RECENTS_SHOW_SEARCH_BAR_VALIDATOR);
            VALIDATORS.put("navigation_bar_left", NAVBAR_LEFT_IN_LANDSCAPE_VALIDATOR);
            VALIDATORS.put("t9_search_input_locale", T9_SEARCH_INPUT_LOCALE_VALIDATOR);
            VALIDATORS.put("bluetooth_accept_all_files", BLUETOOTH_ACCEPT_ALL_FILES_VALIDATOR);
            VALIDATORS.put("lockscreen_scramble_pin_layout", LOCKSCREEN_PIN_SCRAMBLE_LAYOUT_VALIDATOR);
            VALIDATORS.put("lockscreen_rotation", LOCKSCREEN_ROTATION_VALIDATOR);
            VALIDATORS.put("show_alarm_icon", SHOW_ALARM_ICON_VALIDATOR);
            VALIDATORS.put("show_next_alarm", SHOW_NEXT_ALARM_VALIDATOR);
            VALIDATORS.put("safe_headset_volume", SAFE_HEADSET_VOLUME_VALIDATOR);
            VALIDATORS.put("stream_volume_steps_changed", STREAM_VOLUME_STEPS_CHANGED_VALIDATOR);
            VALIDATORS.put("status_bar_ime_switcher", STATUS_BAR_IME_SWITCHER_VALIDATOR);
            VALIDATORS.put("qs_quick_pulldown", STATUS_BAR_QUICK_QS_PULLDOWN_VALIDATOR);
            VALIDATORS.put("qs_show_brightness_slider", QS_SHOW_BRIGHTNESS_SLIDER_VALIDATOR);
            VALIDATORS.put("status_bar_brightness_control", STATUS_BAR_BRIGHTNESS_CONTROL_VALIDATOR);
            VALIDATORS.put("volbtn_music_controls", VOLBTN_MUSIC_CONTROLS_VALIDATOR);
            VALIDATORS.put("edge_service_for_gestures", USE_EDGE_SERVICE_FOR_GESTURES_VALIDATOR);
            VALIDATORS.put("status_bar_notif_count", STATUS_BAR_NOTIF_COUNT_VALIDATOR);
            VALIDATORS.put("call_recording_format", CALL_RECORDING_FORMAT_VALIDATOR);
            VALIDATORS.put("notification_light_brightness_level", NOTIFICATION_LIGHT_BRIGHTNESS_LEVEL_VALIDATOR);
            VALIDATORS.put("notification_light_multiple_leds_enable", NOTIFICATION_LIGHT_MULTIPLE_LEDS_ENABLE_VALIDATOR);
            VALIDATORS.put("notification_light_screen_on_enable", NOTIFICATION_LIGHT_SCREEN_ON_VALIDATOR);
            VALIDATORS.put("notification_light_pulse_default_color", NOTIFICATION_LIGHT_PULSE_DEFAULT_COLOR_VALIDATOR);
            VALIDATORS.put("notification_light_pulse_default_led_on", NOTIFICATION_LIGHT_PULSE_DEFAULT_LED_ON_VALIDATOR);
            VALIDATORS.put("notification_light_pulse_default_led_off", NOTIFICATION_LIGHT_PULSE_DEFAULT_LED_OFF_VALIDATOR);
            VALIDATORS.put("notification_light_pulse_call_color", NOTIFICATION_LIGHT_PULSE_CALL_COLOR_VALIDATOR);
            VALIDATORS.put("notification_light_pulse_call_led_on", NOTIFICATION_LIGHT_PULSE_CALL_LED_ON_VALIDATOR);
            VALIDATORS.put("notification_light_pulse_call_led_off", NOTIFICATION_LIGHT_PULSE_CALL_LED_OFF_VALIDATOR);
            VALIDATORS.put("notification_light_pulse_vmail_color", NOTIFICATION_LIGHT_PULSE_VMAIL_COLOR_VALIDATOR);
            VALIDATORS.put("notification_light_pulse_vmail_led_on", NOTIFICATION_LIGHT_PULSE_VMAIL_LED_ON_VALIDATOR);
            VALIDATORS.put("notification_light_pulse_vmail_led_off", NOTIFICATION_LIGHT_PULSE_VMAIL_LED_OFF_VALIDATOR);
            VALIDATORS.put("notification_light_pulse_custom_enable", NOTIFICATION_LIGHT_PULSE_CUSTOM_ENABLE_VALIDATOR);
            VALIDATORS.put("notification_light_pulse_custom_values", NOTIFICATION_LIGHT_PULSE_CUSTOM_VALUES_VALIDATOR);
            VALIDATORS.put("notification_light_color_auto", NOTIFICATION_LIGHT_COLOR_AUTO_VALIDATOR);
            VALIDATORS.put("headset_connect_player", HEADSET_CONNECT_PLAYER_VALIDATOR);
            VALIDATORS.put("allow_lights", ZEN_ALLOW_LIGHTS_VALIDATOR);
            VALIDATORS.put("zen_priority_allow_lights", ZEN_PRIORITY_ALLOW_LIGHTS_VALIDATOR);
            VALIDATORS.put("touchscreen_gesture_haptic_feedback", TOUCHSCREEN_GESTURE_HAPTIC_FEEDBACK_VALIDATOR);
            VALIDATORS.put("heads_up_custom_values", HEADS_UP_CUSTOM_VALUES_VALIDATOR);
            VALIDATORS.put("heads_up_blacklist_values", HEADS_UP_BLACKLIST_VALUES_VALIDATOR);
            VALIDATORS.put("heads_up_whitelist_values", HEADS_UP_WHITELIST_VALUES_VALIDATOR);
            VALIDATORS.put("___magical_test_passing_enabler", __MAGICAL_TEST_PASSING_ENABLER_VALIDATOR);
        }

        public static String getString(ContentResolver resolver, String name) {
            return getStringForUser(resolver, name, UserHandle.myUserId());
        }

        public static String getStringForUser(ContentResolver resolver, String name, int userId) {
            if (!MOVED_TO_SECURE.contains(name)) {
                return sNameValueCache.getStringForUser(resolver, name, userId);
            }
            Log.w("CMSettings", "Setting " + name + " has moved from CMSettings.System" + " to CMSettings.Secure, value is unchanged.");
            return Secure.getStringForUser(resolver, name, userId);
        }

        public static boolean putString(ContentResolver resolver, String name, String value) {
            return putStringForUser(resolver, name, value, UserHandle.myUserId());
        }

        public static boolean putStringForUser(ContentResolver resolver, String name, String value, int userId) {
            if (!MOVED_TO_SECURE.contains(name)) {
                return sNameValueCache.putStringForUser(resolver, name, value, userId);
            }
            Log.w("CMSettings", "Setting " + name + " has moved from CMSettings.System" + " to CMSettings.Secure, value is unchanged.");
            return false;
        }

        public static int getInt(ContentResolver cr, String name, int def) {
            return getIntForUser(cr, name, def, UserHandle.myUserId());
        }

        public static int getIntForUser(ContentResolver cr, String name, int def, int userId) {
            String v = getStringForUser(cr, name, userId);
            if (v != null) {
                try {
                    def = Integer.parseInt(v);
                } catch (NumberFormatException e) {
                    return def;
                }
            }
            return def;
        }

        public static boolean putInt(ContentResolver cr, String name, int value) {
            return putIntForUser(cr, name, value, UserHandle.myUserId());
        }

        public static boolean putIntForUser(ContentResolver cr, String name, int value, int userId) {
            return putStringForUser(cr, name, Integer.toString(value), userId);
        }
    }

    static {
        sBooleanValidator = new DiscreteValueValidator(new String[]{"0", "1"});
        sNonNegativeIntegerValidator = new C05751();
        sUriValidator = new C05762();
        sColorValidator = new InclusiveIntegerRangeValidator(ExploreByTouchHelper.INVALID_ID, Integer.MAX_VALUE);
        sAlwaysTrueValidator = new C05773();
    }
}
