-processkotlinnullchecks remove_message

# Remove all Android Log.* calls in release (AGP 9.1+; ASSERT = level 7 = all levels)
-maximumremovedandroidloglevel ASSERT

-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite* {
   <fields>;
}

-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn org.slf4j.**