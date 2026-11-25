# R8 rules for release builds. Libraries ship their own keep rules, and
# Android components in the manifest are kept automatically.

# Shrink and optimize, but keep class and method names so crash stack
# traces read as they do in debug builds, without a mapping file.
-dontobfuscate

# Keep line numbers in stack traces.
-keepattributes SourceFile,LineNumberTable
