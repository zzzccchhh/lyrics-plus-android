# ProGuard rules for Lyrics Plus Android

# 1. Keep JavaScript Interface methods to prevent them from being stripped or renamed.
# This ensures WebView to native bridge calls function correctly at runtime.
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# 2. Keep Kuromoji Tokenizer and IPADIC dictionary classes.
# Kuromoji loads dictionary binaries from its jar resources using reflection and class loading.
# Obfuscating these classes can break dictionary initialization.
-keep class com.atilika.kuromoji.** { *; }

# 3. OkHttp specific rules to preserve signatures and prevent optimization warnings.
-keepattributes Signature, *Annotation*, InnerClasses
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**

# 4. Advanced Code Size & Obfuscation Optimizations
# Repackage all obfuscated classes into a flat root package 'a' to reduce Dex constant pool size
-repackageclasses 'a'

# Allow visibility changes to facilitate aggressive class merging and inlining
-allowaccessmodification

