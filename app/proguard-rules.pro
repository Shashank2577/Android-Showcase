# The Desert Ant AARs ship their own consumer rules for their JNI entry points.
# Keep the SDK's public model classes intact so stack traces stay readable.
-keep class ai.desertant.** { *; }

# ONNX Runtime (Hindi speech): its native code finds these classes and their
# constructors by name, so R8 must not rename or strip them.
-keep class ai.onnxruntime.** { *; }

# LiteRT-LM (ask Burrow): its native code calls back into these classes by name,
# and it reads Message and tool classes with Gson and reflection.
-keep class com.google.ai.edge.litertlm.** { *; }
-dontwarn com.google.ai.edge.litertlm.**
