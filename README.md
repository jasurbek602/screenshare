# Screen Share Demo (o'quv loyihasi)

Bu loyiha — foydalanuvchi tugma bosib, tizim rozilik dialogini tasdiqlagandan keyin
ekran tasvirini WebSocket orqali serverga uzatadigan oddiy Android ilovasi.

Xavfsizlik jihatidan muhim: uzatish faqat foydalanuvchi ochiq-oydin ruxsat bergandan
keyin boshlanadi, va ishlab turgan vaqtda doimiy bildirishnoma ko'rinib turadi —
buni dastur kodi orqali yashirib bo'lmaydi (bu Androidning tizimli himoyasi).

## Loyiha tarkibi

```
ScreenShareApp/
 ├─ app/                         ← Android ilova kodi
 │   └─ src/main/
 │       ├─ AndroidManifest.xml
 │       ├─ java/.../MainActivity.kt
 │       ├─ java/.../ScreenCaptureService.kt
 │       └─ res/...
 ├─ server/                      ← Kadrlarni qabul qiluvchi Node.js server
 │   ├─ server.js
 │   ├─ viewer.html
 │   └─ package.json
 └─ .github/workflows/build.yml  ← GitHub Actions orqali avtomatik APK build
```

## 1-qadam: Serverni ishga tushirish

Kompyuteringizda Node.js o'rnatilgan bo'lishi kerak (nodejs.org).

```bash
cd server
npm install
npm start
```

Server `http://localhost:8080` da ishga tushadi. Brauzerda shu manzilni oching —
kadrlar shu yerda ko'rinadi.

**Muhim:** kompyuteringizning lokal IP manzilini bilib oling (masalan `192.168.1.100`),
chunki telefon shu IP orqali serverga ulanadi. Windows'da `ipconfig`,
Mac/Linux'da `ifconfig` yoki `ip addr` buyrug'i bilan ko'rish mumkin.
Telefon va kompyuter bir xil Wi-Fi tarmog'ida bo'lishi shart.

## 2-qadam: Android kodida server manzilini sozlash

`app/src/main/java/com/example/screenshare/ScreenCaptureService.kt` faylida:

```kotlin
private val SERVER_URL = "ws://192.168.1.100:8080"
```

qatoridagi IP manzilni o'zingizning kompyuteringiz IP manziliga almashtiring.

## 3-qadam: APK yasash

### A) GitHub Actions orqali (Android Studio shart emas)

1. Ushbu papkani GitHub'dagi yangi repozitoriyga yuklang:
   ```bash
   cd ScreenShareApp
   git init
   git add .
   git commit -m "Boshlang'ich versiya"
   git branch -M main
   git remote add origin https://github.com/FOYDALANUVCHI_NOMI/REPO_NOMI.git
   git push -u origin main
   ```
2. GitHub'da repozitoriyingizga o'ting → **Actions** bo'limi → build avtomatik boshlanadi
3. Build tugagach (~3-5 daqiqa), natija sahifasidagi **Artifacts** bo'limidan
   `app-debug-apk` faylini yuklab oling — bu tayyor `.apk`

### B) Android Studio orqali (agar o'rnatilgan bo'lsa)

1. Android Studio'da **Open** → shu papkani tanlang
2. Gradle sinxronlanishini kuting
3. **Run ▶** tugmasini bosing (qurilma ulangan yoki emulyator ishga tushirilgan bo'lsin)

## 4-qadam: Ilovani sinash

1. Telefonga APK'ni o'rnating (`adb install app-debug.apk` yoki faylni to'g'ridan-to'g'ri ochib)
2. Telefon va kompyuter bir xil Wi-Fi tarmog'ida ekanini tekshiring
3. Serverni ishga tushiring (`npm start`)
4. Telefonda ilovani oching → **"Ekranni ulashishni boshlash"** tugmasini bosing
5. Tizim rozilik dialogida **"Start now"** ni tasdiqlang
6. Kompyuterda `http://localhost:8080` sahifasini oching — telefon ekranining
   kadrlari shu yerda jonli ko'rina boshlaydi

## Cheklovlar va eslatmalar

- Bu — **o'quv/demo** darajasidagi kod. Ishlab chiqarish (production) uchun
  TLS (wss://), autentifikatsiya va xatoliklarni qayta ulanish (reconnect) logikasi qo'shilishi kerak.
- JPEG orqali xom kadr uzatish katta trafik sarflaydi; jiddiy loyihada
  H.264 video kodek (`MediaCodec`) yoki WebRTC ishlatish tavsiya etiladi.
- Foreground service bildirishnomasi va tizim rozilik dialogi — bu ataylab olib
  tashlab bo'lmaydigan xavfsizlik choralari, chunki ular foydalanuvchini
  ekrani kuzatilayotgani haqida doimiy xabardor qilib turadi.
