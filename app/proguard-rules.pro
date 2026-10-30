# The Desert Ant AARs ship their own consumer rules for their JNI entry points.
# Keep the SDK's public model classes intact so stack traces stay readable.
-keep class ai.desertant.** { *; }

# ONNX Runtime (Hindi speech): its native code finds these classes and their
# constructors by name, so R8 must not rename or strip them.
-keep class ai.onnxruntime.** { *; }
