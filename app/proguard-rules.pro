# Gobind bindings load natives via JNI reflection.
-keep class libv2ray.** { *; }
-keep class go.** { *; }
# WireGuard config parser relies on field names for ini parsing.
-keep class org.amnezia.** { *; }
