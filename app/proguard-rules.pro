# GateKeeper ProGuard Rules
# Keep Firebase classes
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Keep ZXing classes
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.barcodescanner.** { *; }

# Keep GateKeeper receivers (referenced in manifest)
-keep class com.gatekeeper.alert.receiver.** { *; }
-keep class com.gatekeeper.alert.service.** { *; }

