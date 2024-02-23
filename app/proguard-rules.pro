-dontobfuscate
-keepattributes SourceFile,LineNumberTable

# These are mainly needed in full R8 mode, but Connectbot uses these by default
# so we keep these to avoid any breakage.
-keepattributes InnerClasses
-keep public class com.trilead.ssh2.compression.**
-keep public class com.trilead.ssh2.crypto.**


# Needed to keep R8 happy about Tink library (used by sshlib)
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
