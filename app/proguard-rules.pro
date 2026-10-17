# Worker names are persisted in WorkManager's database. Keep them stable across
# upgrades so work enqueued by an older build can still run.
-keepnames class com.streakguard.app.schedule.* extends androidx.work.ListenableWorker
-keepnames class com.streakguard.app.diagnostics.* extends androidx.work.ListenableWorker
# Room resolves its generated implementation by naming convention. Dependencies
# also supply consumer rules; blanket application keep rules are unnecessary.
-keep class com.streakguard.app.data.local.AppDatabase_Impl { <init>(); }
