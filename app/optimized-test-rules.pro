# The framework-only instrumentation bridge calls this entry point reflectively.
# The checks and all their dependencies are otherwise optimized with the QA app.
-keep class com.streakguard.app.OptimizedSmokeChecks {
    public static java.lang.String run(android.content.Context);
    public static java.lang.String verifyRestart(android.content.Context);
}
-keep class com.streakguard.app.DiagnosticsSmokeChecks {
    public static java.lang.String run(android.content.Context);
}
