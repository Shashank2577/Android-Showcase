# The Desert Ant AARs ship their own consumer rules for their JNI entry points.
# Keep the SDK's public model classes intact so stack traces stay readable.
-keep class ai.desertant.** { *; }
