# Room entities are accessed via generated code; keep field names stable across obfuscation.
-keep class com.sandeepraghav.passvault.data.** { *; }

# JavaMail resolves its transport/store providers by class name from
# META-INF/javamail.default.providers, so those names must survive R8.
-keep class javax.mail.** { *; }
-keep class com.sun.mail.** { *; }
-dontwarn javax.mail.**
-dontwarn com.sun.mail.**
-dontwarn java.awt.**
-dontwarn javax.activation.**

# argon2kt crosses into native code via JNI; the bridge classes and the error
# type constructed from C are only referenced by name.
-keep class com.lambdapioneer.argon2kt.** { *; }
