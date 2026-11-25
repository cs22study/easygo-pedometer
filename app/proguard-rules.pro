# ProGuard / R8 rules for EasyGo-Pedometer
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public <init>();
}
