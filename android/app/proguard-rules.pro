# Keep kotlinx.serialization serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.hambalapps.agymobile.data.**$$serializer { *; }
-keepclassmembers class com.hambalapps.agymobile.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.hambalapps.agymobile.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Shizuku UserService & AIDL interfaces
-keep class com.hambalapps.agymobile.data.engine.ShizukuShellService {
    public <init>();
    *;
}
-keep interface com.hambalapps.agymobile.data.engine.IShizukuShellService { *; }
-keep class com.hambalapps.agymobile.data.engine.IShizukuShellService$Stub { *; }
-dontwarn rikka.shizuku.**
