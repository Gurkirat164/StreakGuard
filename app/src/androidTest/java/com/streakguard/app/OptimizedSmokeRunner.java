package com.streakguard.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;

/** Framework-only bridge; the test checks themselves are R8 optimized with the QA app. */
public final class OptimizedSmokeRunner extends Instrumentation {
    private boolean restart;
    private boolean diagnostics;
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        restart = arguments != null && "true".equals(arguments.getString("restart"));
        diagnostics = arguments != null && "true".equals(arguments.getString("diagnostics"));
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            // start() launches this thread before Application.onCreate finishes on a cold process.
            waitForIdleSync();
            Context context = getTargetContext();
            if (!context.getPackageName().endsWith(".qa")) {
                throw new IllegalStateException("Smoke tests require the isolated .qa install");
            }
            Class<?> checks = context.getClassLoader().loadClass(diagnostics
                ? "com.streakguard.app.DiagnosticsSmokeChecks" : "com.streakguard.app.OptimizedSmokeChecks");
            String message = (String) checks.getMethod(restart ? "verifyRestart" : "run", Context.class).invoke(null, context);
            result.putString("stream", message + "\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", "FAIL: " + android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
