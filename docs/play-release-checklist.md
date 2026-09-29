# Play kapalı test kontrol listesi

Geliştirici: Ogitech Studio. Uygulama: SorGez. Paket: com.ogzhngms.sorgez.

## Repoda hazır olanlar

- [x] Release imzalama ayarı (`keystore.properties`, git'e girmez)
- [x] Uygulama içi dil seçimi için dil bölmesi kapalı (`bundle.language.enableSplit = false`)
- [x] Sürüm numarası: `-PversionCode=… -PversionName=…` ile verilebilir, varsayılan 1 / 1.0
- [x] Gizlilik politikası: https://ogzhngms.github.io/sorgez/privacy.html (profil > Hakkında'da link)
- [x] Mağaza metinleri: `play-store-tr.md`, `play-store-en.md`

## Senin yapacakların

1. Yükleme anahtarı oluştur (bir kez; şifreleri sen belirle, kaybetme):
   ```bash
   keytool -genkeypair -v -keystore ~/Developer/ogi/keys/sorgez-upload.jks -alias sorgez -keyalg RSA -keysize 2048 -validity 10000
   ```
2. `sorgez-android/keystore.properties` dosyasını oluştur:
   ```properties
   storeFile=/Users/oguzhangumus/Developer/ogi/keys/sorgez-upload.jks
   storePassword=…
   keyAlias=sorgez
   keyPassword=…
   ```
3. AAB'yi derle: `./gradlew bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`
4. Play Console: uygulamayı oluştur, AAB'yi **Kapalı test** kanalına yükle.
5. Play Console > Uygulama bütünlüğü: Play App Signing sertifikasının **SHA-256** değerini kopyala.
6. Firebase > App Check > SorGez > **Play Integrity**: SHA-256'yı ekleyip kaydet. Bu yapılmadan Play'den indirilen sürüm Gemini'ye ulaşamaz.
7. Play Console > Uygulama bütünlüğü > Play Integrity API'yi Firebase projesine (ogi-seyahatname) bağla.
8. Mağaza girişi: metinler, 512×512 ikon, 1024×500 özellik grafiği, en az 2 ekran görüntüsü.
9. Uygulama içeriği: gizlilik URL'si, reklam yok, içerik derecelendirmesi, hedef kitle (13+), Veri güvenliği.
10. En az 12 test kullanıcısı ekle, katılım (opt-in) linkini paylaş; 14 gün kesintisiz test.

## Veri güvenliği beyanı için

- Toplanan: uygulama etkileşimi değil; **uygulama içi girdi** (gezi cevapları, notlar) Gemini'ye plan için gönderilir, saklanmaz.
- Cihaz/diğer kimlik: anonim Firebase kullanıcı kimliği; dil ve para birimi tercihi Firestore'da.
- Amaç: uygulama işlevselliği ve güvenlik (App Check). Reklam/analitik yok. Aktarımda şifreli.
- Silme: e-posta ile talep (gizlilik politikasında yazıyor).
