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
# Chave de assinatura e senha ficam FORA do repositório.
# Nunca gerar outra chave: um APK com chave nova não atualiza o app instalado.
CHAVE=${CHAVE:-$HOME/.config/chaves-android/vcf-nfc-card.jks}
SENHA=${SENHA:-$HOME/.config/chaves-android/vcf-nfc-card.senha}
[ -f "$CHAVE" ] || { echo "ERRO: chave de assinatura não encontrada em $CHAVE. Restaure o backup; não gere outra." >&2; exit 1; }
[ -f "$SENHA" ] || { echo "ERRO: arquivo da senha não encontrado em $SENHA." >&2; exit 1; }
$BT/apksigner sign --ks "$CHAVE" --ks-pass "file:$SENHA" --out vcf-nfc-card.apk out/a.apk
rm -f vcf-nfc-card.apk.idsig
ls -la vcf-nfc-card.apk
