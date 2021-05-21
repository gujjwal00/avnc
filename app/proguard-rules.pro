-dontobfuscate
-keepattributes SourceFile,LineNumberTable

##########################################################################
# Rules for keeping JSON Serialization code
# Ref: https://github.com/Kotlin/kotlinx.serialization#android
##########################################################################

-keepattributes *Annotation*, InnerClasses

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.gaurav.avnc.**$$serializer { *; }
-keepclassmembers class com.gaurav.avnc.** {
    *** Companion;
}
-keepclasseswithmembers class com.gaurav.avnc.** {
    kotlinx.serialization.KSerializer serializer(...);
}