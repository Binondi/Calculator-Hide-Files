# Keep your MainActivity and Application class
-keep public class devs.org.calculator.activities.MainActivity
-keep public class devs.org.calculator.activities.SetupPasswordActivity
-keep public class devs.org.calculator.activities.HiddenVaultActivity
-keep public class devs.org.calculator.** { *; }

# Keep exp4j library since it's used for expression evaluation
-keep class net.objecthunter.exp4j.** { *; }
-dontwarn net.objecthunter.exp4j.**

# Keep Google Material components
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# Keep Android X components
-keep class androidx.** { *; }
-keep interface androidx.** { *; }

# Keep any classes with ViewBinding
-keep class devs.org.calculator.databinding.** { *; }

# Keep any callback interfaces
-keep class devs.org.calculator.callbacks.** { *; }
-keep interface devs.org.calculator.callbacks.** { *; }

# Keep classes used for regex pattern matching
-keep class java.util.regex.** { *; }

# Keep annotation classes
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable

# Keep Parcelable classes (might be needed for Intent extras)
-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

# Keep FileManager classes since they work with storage permissions
-keep class devs.org.calculator.utils.FileManager { *; }

# Keep DialogUtil since it's used for permission dialogs
-keep class devs.org.calculator.utils.DialogUtil { *; }

# Keep PrefsUtil since it's used for password validation
-keep class devs.org.calculator.utils.PrefsUtil { *; }

# General Android rules
-keepclassmembers class * extends android.app.Activity {
   public void *(android.view.View);
}

# Keep any classes that use reflection
-keepattributes InnerClasses

# Keep R classes and their fields
-keepclassmembers class **.R$* {
    public static <fields>;
}

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep specific activities with special code in onCreate
-keepclassmembers class * extends android.app.Activity {
    public void onCreate(android.os.Bundle);
}