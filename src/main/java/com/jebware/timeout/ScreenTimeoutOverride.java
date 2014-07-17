package com.jebware.timeout;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

/**
 * Allows you to override the device's automatic screen timeout for a specified amount of time.
 * You specify the time, in seconds, and the screen will not timeout for that long after a touch
 * event.
 *
 * Created by jware on 7/17/14.
 * (c) 2014 Liitaa, LLC
 */
public class ScreenTimeoutOverride {

    /**
     * Any calls to this class must be done on the main UI thread.
     *
     * Calls from any other thread will throw this exception
     */
    public class CalledFromWrongThreadException extends RuntimeException {
        public CalledFromWrongThreadException(String message) {
            super(message);
        }
    }

    public interface OnTimerCompleteListener {
        /**
         * Called when the timer has counted down to 0.  At this time,
         * FLAG_KEEP_SCREEN_ON is removed from the Window, and the screen
         * timeout will kick in sometime in the future.
         */
        public void onTimerComplete();
    }

    //private static final String TAG = "ScreenTimeout";

    private static final long TICK_LENGTH_MSEC = 500; // tick every 500 msec

    private Window.Callback passthrough;
    private Window window;
    private Handler handler;
    private OnTimerCompleteListener listener;

    private long timeoutMillis;
    private long timerTarget;

    private boolean tickScheduled = false;

    public ScreenTimeoutOverride(long timeoutSeconds, Window window) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new CalledFromWrongThreadException("Only the UI thread can call functions on ScreenTimeout");
        }
        handler = new Handler();

        timeoutMillis = timeoutSeconds * 1000;
        passthrough = window.getCallback();
        this.window = window;
        window.setCallback(windowCallback);
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        startTimer();
    }

    /**
     * Add a callback to the Window
     * @param callback the callback to add
     */
    public void setWindowCallback(Window.Callback callback) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new CalledFromWrongThreadException("Only the UI thread can call functions on ScreenTimeout");
        }
        passthrough = callback;
    }

    /**
     * Register to be notified when the timer runs out.
     *
     * Your listener will be called when the timer has counted down to 0.
     * At this time, FLAG_KEEP_SCREEN_ON is removed from the Window, and
     * the screen timeout will happen sometime in the future.
     */
    public void setOnTimerCompleteListener(OnTimerCompleteListener onTimerCompleteListener) {
        listener = onTimerCompleteListener;
    }

    /**
     * Start the countdowm timer.  If the timer ws already running, we'll reset the countdown
     * to the given timeout value.
     *
     * This is called automatically by the constructor, so you don't have to call this on creation.
     * Instead, call this if you want to reset the timer for some even other than a touch.
     */
    public void startTimer() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new CalledFromWrongThreadException("Only the UI thread can call functions on ScreenTimeout");
        }
        resetTimer();
    }

    /**
     * Clear the timer and remove FLAG_KEEP_SCREEN_ON
     *
     * Call this when you no longer want to override the device screen timeout.
     */
    public void clear() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new CalledFromWrongThreadException("Only the UI thread can call functions on ScreenTimeout");
        }
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        handler.removeCallbacks(tick);
        tickScheduled = false;

        window.setCallback(passthrough);
        passthrough = null;
    }

    private void resetTimer() {
        timerTarget = SystemClock.uptimeMillis() + timeoutMillis;
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (window.getCallback() != windowCallback) {
            passthrough = window.getCallback();
            window.setCallback(windowCallback);
        }

        if (!tickScheduled) {
            handler.postDelayed(tick, TICK_LENGTH_MSEC);
            tickScheduled = true;
        }
    }

    private void onTimerComplete() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (listener != null) {
            listener.onTimerComplete();
        }
    }

    private Runnable tick = new Runnable() {
        @Override
        public void run() {
            tickScheduled = false;

            if (SystemClock.uptimeMillis() >= timerTarget) {
                onTimerComplete();
            } else {
                handler.postDelayed(tick, TICK_LENGTH_MSEC);
                tickScheduled = true;
            }
        }
    };

    /**
     * passes all events through to passthrough, forwarding its return values
     *
     * only purpose is to intercept dispatch calls to know when to reset the timer
     */
    @SuppressWarnings("FieldCanBeLocal")
    private Window.Callback windowCallback = new Window.Callback() {
        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            return passthrough != null && passthrough.dispatchKeyEvent(event);
        }

        @TargetApi(Build.VERSION_CODES.HONEYCOMB)
        @Override
        public boolean dispatchKeyShortcutEvent(KeyEvent event) {
            return passthrough != null && passthrough.dispatchKeyShortcutEvent(event);
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            //User touched the screen, reset the timeout
            resetTimer();

            return passthrough != null && passthrough.dispatchTouchEvent(event);
        }

        @Override
        public boolean dispatchTrackballEvent(MotionEvent event) {
            return passthrough != null && passthrough.dispatchTrackballEvent(event);
        }

        @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
        @Override
        public boolean dispatchGenericMotionEvent(MotionEvent event) {
            return passthrough != null && passthrough.dispatchGenericMotionEvent(event);
        }

        @TargetApi(Build.VERSION_CODES.DONUT)
        @Override
        public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
            return passthrough != null && passthrough.dispatchPopulateAccessibilityEvent(event);
        }

        @Override
        public View onCreatePanelView(int featureId) {
            if (passthrough != null) {
                return passthrough.onCreatePanelView(featureId);
            } else {
                return null;
            }
        }

        @Override
        public boolean onCreatePanelMenu(int featureId, Menu menu) {
            return passthrough != null && passthrough.onCreatePanelMenu(featureId, menu);
        }

        @Override
        public boolean onPreparePanel(int featureId, View view, Menu menu) {
            return passthrough != null && passthrough.onPreparePanel(featureId, view, menu);
        }

        @Override
        public boolean onMenuOpened(int featureId, Menu menu) {
            return passthrough != null && passthrough.onMenuOpened(featureId, menu);
        }

        @Override
        public boolean onMenuItemSelected(int featureId, MenuItem item) {
            return passthrough != null && passthrough.onMenuItemSelected(featureId, item);
        }

        @Override
        public void onWindowAttributesChanged(WindowManager.LayoutParams attrs) {
            if (passthrough != null) {
                passthrough.onWindowAttributesChanged(attrs);
            }
        }

        @Override
        public void onContentChanged() {
            if (passthrough != null) {
                passthrough.onContentChanged();
            }
        }

        @Override
        public void onWindowFocusChanged(boolean hasFocus) {
            if (passthrough != null) {
                passthrough.onWindowFocusChanged(hasFocus);
            }
        }

        @TargetApi(Build.VERSION_CODES.ECLAIR)
        @Override
        public void onAttachedToWindow() {
            if (passthrough != null) {
                passthrough.onAttachedToWindow();
            }
        }

        @TargetApi(Build.VERSION_CODES.ECLAIR)
        @Override
        public void onDetachedFromWindow() {
            if (passthrough != null) {
                passthrough.onDetachedFromWindow();
            }
        }

        @Override
        public void onPanelClosed(int featureId, Menu menu) {
            if (passthrough != null) {
                passthrough.onPanelClosed(featureId, menu);
            }
        }

        @Override
        public boolean onSearchRequested() {
            return passthrough != null && passthrough.onSearchRequested();
        }

        @TargetApi(Build.VERSION_CODES.HONEYCOMB)
        @Override
        public ActionMode onWindowStartingActionMode(ActionMode.Callback callback) {
            if (passthrough != null) {
                return passthrough.onWindowStartingActionMode(callback);
            } else {
                return null;
            }
        }

        @TargetApi(Build.VERSION_CODES.HONEYCOMB)
        @Override
        public void onActionModeStarted(ActionMode mode) {
            if (passthrough != null) {
                passthrough.onActionModeStarted(mode);
            }
        }

        @TargetApi(Build.VERSION_CODES.HONEYCOMB)
        @Override
        public void onActionModeFinished(ActionMode mode) {
            if (passthrough != null) {
                passthrough.onActionModeFinished(mode);
            }
        }
    };

}
