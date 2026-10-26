# كود بناء ملف الـ APK التلقائي لتطبيق AirPointer (جاهز للنسخ بنقرة واحدة)

**المشروع:** AirPointer (مؤشر سطح المكتب بالهواء)  
**المطور:** MOUNAIM IA  
**المسار في GitHub:** `.github/workflows/build-apk.yml`

---

```yaml
name: Build AirPointer Standalone APK

on:
  push:
    branches: [ main, master ]
  workflow_dispatch:

jobs:
  build:
    name: Build Android APK
    runs-on: ubuntu-latest

    steps:
      - name: 1. Setup Java 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'

      - name: 2. Build AirPointer APK
        run: |
          mkdir -p airapp/app/src/main/java/ai/mounaim/airpointer
          mkdir -p airapp/app/src/main/res/values
          cd airapp
          gradle wrapper
          
          cat << 'EOF' > settings.gradle
          rootProject.name = "AirPointer"
          include ':app'
          EOF

          cat << 'EOF' > build.gradle
          buildscript {
              repositories { google(); mavenCentral() }
              dependencies { classpath 'com.android.tools.build:gradle:8.2.2' }
          }
          allprojects {
              repositories { google(); mavenCentral() }
          }
          EOF

          cat << 'EOF' > app/build.gradle
          apply plugin: 'com.android.application'
          android {
              namespace 'ai.mounaim.airpointer'
              compileSdk 34
              defaultConfig {
                  applicationId "ai.mounaim.airpointer"
                  minSdk 26
                  targetSdk 34
                  versionCode 1
                  versionName "1.0.0"
              }
              buildTypes {
                  release {
                      minifyEnabled false
                  }
              }
          }
          dependencies {
              implementation 'androidx.appcompat:appcompat:1.6.1'
              implementation 'com.google.android.material:material:1.11.0'
          }
          EOF

          cat << 'EOF' > app/src/main/AndroidManifest.xml
          <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="ai.mounaim.airpointer">
              <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>
              <uses-permission android:name="android.permission.CAMERA"/>
              <application android:label="AirPointer" android:theme="@style/Theme.AppCompat.Light.NoActionBar">
                  <activity android:name=".MainActivity" android:exported="true">
                      <intent-filter>
                          <action android:name="android.intent.action.MAIN"/>
                          <category android:name="android.intent.category.LAUNCHER"/>
                      </intent-filter>
                  </activity>
              </application>
          </manifest>
          EOF

          cat << 'EOF' > app/src/main/java/ai/mounaim/airpointer/MainActivity.kt
          package ai.mounaim.airpointer
          import android.os.Bundle
          import androidx.appcompat.app.AppCompatActivity
          class MainActivity : AppCompatActivity() {
              override fun onCreate(savedInstanceState: Bundle?) {
                  super.onCreate(savedInstanceState)
              }
          }
          EOF

          ./gradlew assembleDebug --stacktrace

      - name: 3. Upload APK File
        uses: actions/upload-artifact@v4
        with:
          name: AirPointer-v1.0.0-Universal
          path: airapp/app/build/outputs/apk/debug/*.apk
          retention-days: 7
```
