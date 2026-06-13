-optimizationpasses 5
-allowaccessmodification
-repackageclasses ''
-overloadaggressively

-keep class io.github.liuanxin.lunarclock.MinimalWidgetProvider { public <init>(); }

-dontwarn androidx.appcompat.**
-dontwarn androidx.core.**
