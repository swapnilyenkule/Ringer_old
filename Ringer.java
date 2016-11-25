package com.android.server.telecom;

import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioAttributes.Builder;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemVibrator;
import android.os.Vibrator;
import android.provider.Settings;
import android.provider.Settings.Global;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;
import com.android.server.telecom.InCallTonePlayer.Factory;
import cyanogenmod.providers.CMSettings.System;
import java.util.LinkedList;
import java.util.List;

final class Ringer extends CallsManagerListenerBase {
    private static final AudioAttributes VIBRATION_ATTRIBUTES;
    private static final long[] VIBRATION_PATTERN;
    private final CallAudioManager mCallAudioManager;
    private InCallTonePlayer mCallWaitingPlayer;
    private final CallsManager mCallsManager;
    private final Context mContext;
    private boolean mIsVibrating;
    private final Factory mPlayerFactory;
    private final List<Call> mRingingCalls;
    private final AsyncRingtonePlayer mRingtonePlayer;
    private int mState;
    private final Vibrator mVibrator;

    static {
        VIBRATION_PATTERN = new long[]{0, 1000, 1000};
        VIBRATION_ATTRIBUTES = new Builder().setContentType(4).setUsage(6).build();
    }

    Ringer(CallAudioManager callAudioManager, CallsManager callsManager, Factory playerFactory, Context context) {
        this.mRingingCalls = new LinkedList();
        this.mState = 3;
        this.mIsVibrating = false;
        this.mCallAudioManager = callAudioManager;
        this.mCallsManager = callsManager;
        this.mPlayerFactory = playerFactory;
        this.mContext = context;
        this.mVibrator = new SystemVibrator(context);
        this.mRingtonePlayer = new AsyncRingtonePlayer(context);
    }

    public void onCallAdded(Call call) {
        if (call.isIncoming() && call.getState() == 4) {
            if (this.mRingingCalls.contains(call)) {
                Log.wtf((Object) this, "New ringing call is already in list of unanswered calls", new Object[0]);
            }
            this.mRingingCalls.add(call);
            updateRinging(call);
        }
    }

    public void onCallRemoved(Call call) {
        removeFromUnansweredCall(call);
    }

    public void onCallStateChanged(Call call, int oldState, int newState) {
        if (newState != 4) {
            removeFromUnansweredCall(call);
        }
    }

    public void onIncomingCallAnswered(Call call) {
        onRespondedToIncomingCall(call);
    }

    public void onIncomingCallRejected(Call call, boolean rejectWithMessage, String textMessage) {
        onRespondedToIncomingCall(call);
    }

    public void onForegroundCallChanged(Call oldForegroundCall, Call newForegroundCall) {
        Call ringingCall = null;
        if (this.mRingingCalls.contains(newForegroundCall)) {
            ringingCall = newForegroundCall;
        } else if (this.mRingingCalls.contains(oldForegroundCall)) {
            ringingCall = oldForegroundCall;
        }
        if (ringingCall != null) {
            updateRinging(ringingCall);
        }
    }

    void silence() {
        for (Call call : this.mRingingCalls) {
            call.silence();
        }
        this.mRingingCalls.clear();
        updateRinging(null);
    }

    private void onRespondedToIncomingCall(Call call) {
        if (getTopMostUnansweredCall() == call) {
            removeFromUnansweredCall(call);
        }
    }

    private Call getTopMostUnansweredCall() {
        return this.mRingingCalls.isEmpty() ? null : (Call) this.mRingingCalls.get(0);
    }

    private void removeFromUnansweredCall(Call call) {
        this.mRingingCalls.remove(call);
        updateRinging(call);
    }

    private void updateRinging(Call call) {
        if (this.mRingingCalls.isEmpty()) {
            stopRinging(call, "No more ringing calls found");
            stopCallWaiting(call);
            return;
        }
        startRingingOrCallWaiting(call);
    }

    private void startRingingOrCallWaiting(Call call) {
        Call foregroundCall = this.mCallsManager.getForegroundCall();
        Log.m6v((Object) this, "startRingingOrCallWaiting, foregroundCall: %s.", foregroundCall);
        if (Global.getInt(this.mContext.getContentResolver(), "theater_mode_on", 0) != 1) {
            if (this.mRingingCalls.contains(foregroundCall) && !this.mCallsManager.hasActiveOrHoldingCall()) {
                stopCallWaiting(call);
                if (shouldRingForContact(foregroundCall.getContactUri())) {
                    if (((AudioManager) this.mContext.getSystemService("audio")).getStreamVolume(2) >= 0) {
                        if (this.mState != 1) {
                            Log.event(call, "START_RINGER");
                            this.mState = 1;
                        }
                        float startVolume = 0.0f;
                        int rampUpTime = 0;
                        ContentResolver cr = this.mContext.getContentResolver();
                        if (System.getInt(cr, "increasing_ring", 0) != 0) {
                            startVolume = System.getFloat(cr, "increasing_ring_start_vol", 0.1f);
                            rampUpTime = System.getInt(cr, "increasing_ring_ramp_up_time", 20);
                        }
                        this.mCallAudioManager.setIsRinging(call, true);
                        String foregroundCallId = foregroundCall.getTargetPhoneAccount().getId();
                        int phoneId = 0;
                        if (TextUtils.isDigitsOnly(foregroundCallId)) {
                            phoneId = SubscriptionManager.getPhoneId(Integer.valueOf(foregroundCallId).intValue());
                        }
                        this.mRingtonePlayer.setPhoneId(phoneId);
                        this.mRingtonePlayer.play(foregroundCall.getRingtone(), startVolume, rampUpTime);
                    } else {
                        Log.m6v((Object) this, "startRingingOrCallWaiting, skipping because volume is 0", new Object[0]);
                    }
                    if (shouldVibrate(this.mContext) && !this.mIsVibrating) {
                        this.mVibrator.vibrate(VIBRATION_PATTERN, 1, VIBRATION_ATTRIBUTES);
                        this.mIsVibrating = true;
                    }
                }
            } else if (foregroundCall != null) {
                Log.m6v((Object) this, "Playing call-waiting tone.", new Object[0]);
                stopRinging(call, "Stop for call-waiting");
                if (this.mState != 2) {
                    Log.event(call, "START_CALL_WAITING_TONE");
                    this.mState = 2;
                }
                if (this.mCallWaitingPlayer == null) {
                    this.mCallWaitingPlayer = this.mPlayerFactory.createPlayer(4);
                    this.mCallWaitingPlayer.startTone();
                }
            }
        }
    }

    private boolean shouldRingForContact(Uri contactUri) {
        NotificationManager manager = (NotificationManager) this.mContext.getSystemService("notification");
        Bundle extras = new Bundle();
        if (contactUri != null) {
            extras.putStringArray("android.people", new String[]{contactUri.toString()});
        }
        return manager.matchesCallFilter(extras);
    }

    private void stopRinging(Call call, String reasonTag) {
        if (this.mState == 1) {
            Log.event(call, "STOP_RINGER", reasonTag);
            this.mState = 3;
        }
        this.mRingtonePlayer.stop();
        if (this.mIsVibrating) {
            this.mVibrator.cancel();
            this.mIsVibrating = false;
        }
        this.mCallAudioManager.setIsRinging(call, false);
    }

    private void stopCallWaiting(Call call) {
        Log.m6v((Object) this, "stop call waiting.", new Object[0]);
        if (this.mCallWaitingPlayer != null) {
            this.mCallWaitingPlayer.stopTone();
            this.mCallWaitingPlayer = null;
        }
        if (this.mState == 2) {
            Log.event(call, "STOP_CALL_WAITING_TONE");
            this.mState = 3;
        }
    }

    private boolean shouldVibrate(Context context) {
        boolean z = true;
        int ringerMode = ((AudioManager) context.getSystemService("audio")).getRingerModeInternal();
        if (getVibrateWhenRinging(context)) {
            if (ringerMode == 0) {
                z = false;
            }
            return z;
        }
        if (ringerMode != 1) {
            z = false;
        }
        return z;
    }

    private boolean getVibrateWhenRinging(Context context) {
        boolean z = false;
        if (!this.mVibrator.hasVibrator()) {
            return false;
        }
        if (Settings.System.getInt(context.getContentResolver(), "vibrate_when_ringing", 0) != 0) {
            z = true;
        }
        return z;
    }
}
