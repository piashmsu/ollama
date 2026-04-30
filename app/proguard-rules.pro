# Keep JNI native method holders
-keep class com.piashmsu.aichat.llm.LlamaNative { *; }
-keepclasseswithmembernames class * { native <methods>; }

# Markwon / Prism4j use reflection
-keep class io.noties.markwon.** { *; }
-keep class io.noties.prism4j.** { *; }

# Kotlinx serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
