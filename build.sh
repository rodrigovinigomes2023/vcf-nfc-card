#!/bin/bash
set -e
cd "$(dirname "$0")"
SDK=$HOME/Android/Sdk; BT=$SDK/build-tools/34.0.0; JAR=$SDK/platforms/android-34/android.jar
rm -rf out && mkdir -p out/cls out/dex
$BT/aapt2 compile --dir res -o out/res.zip
$BT/aapt2 link -I $JAR --manifest AndroidManifest.xml -A assets out/res.zip -o out/base.apk
/usr/lib/jvm/java-17-openjdk-amd64/bin/javac -source 8 -target 8 -bootclasspath $JAR:$BT/core-lambda-stubs.jar -classpath $JAR -d out/cls $(find src -name '*.java')
$BT/d8 --min-api 26 --lib $JAR --output out/dex $(find out/cls -name '*.class')
cp out/base.apk out/u.apk && (cd out/dex && zip -q ../u.apk classes.dex)
$BT/zipalign -f 4 out/u.apk out/a.apk
CHAVE=${CHAVE:-$HOME/.config/chaves-android/vcf-nfc-card.jks}
[ -f "$CHAVE" ] || keytool -genkeypair -keystore "$CHAVE" -storepass android -keypass android -alias k -keyalg RSA -validity 10000 -dname CN=nfc >/dev/null 2>&1
$BT/apksigner sign --ks "$CHAVE" --ks-pass pass:android --out vcf-nfc-card.apk out/a.apk
rm -f vcf-nfc-card.apk.idsig
ls -la vcf-nfc-card.apk
