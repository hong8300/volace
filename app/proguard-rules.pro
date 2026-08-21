# Widget providers and the tap receiver are only referenced from AndroidManifest.xml.
# AGP keeps manifest components automatically, but the enum in WidgetStyle also holds
# Class objects for them, so make the intent explicit.
-keep class com.hong.volace.widget.** { *; }

# Room resolves its generated implementation by class name at runtime.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
