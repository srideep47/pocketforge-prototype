# Native entry points are resolved by name from C++.
-keepclasseswithmembernames class * {
    native <methods>;
}
