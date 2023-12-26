-dontobfuscate
-keepattributes SourceFile,LineNumberTable

# Needed to keep R8 happy about Tink library (used by sshlib)
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
