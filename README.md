<div align="center">

# Ekran Süresi · Screen Time & Battery

**A minimal, dark-themed Android app that reads the screen-on time Android keeps hidden from some system battery screens, plus detailed live battery information.**

![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84)
![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-E8B84A)
![Root](https://img.shields.io/badge/root-optional%20for%20some%20data-E0115F)

[English](#english) · [Türkçe](#türkçe)

</div>

---

## English

### What is it?

Some Android skins hide the "screen on time" figure from their built-in battery pages, even though the operating system still records it. **Ekran Süresi** reads that data straight from Android's `batterystats` service and shows it in a clean, dark interface. A second tab shows detailed battery information such as current, voltage, temperature, capacity and cycle count.

### Features

**Screen tab (root required)**
- Screen-on time since the last full charge, shown on an animated ring
- How many times the screen was turned on
- Time on battery, screen-off time and doze / always-on-display time
- Battery drained while the screen was on and off (mAh and %)
- The system's own *estimated* screen-on time, labelled separately as a projection so it is not confused with the measured value

**Battery tab**
- *Live values, no root needed, refreshed every second:* instant current (mA), power (W), voltage, temperature, charging status, power source (AC / USB / wireless), battery health state, technology, average current, charge counter and estimated capacity
- *Values read with root, refreshed when you open the tab:* cycle count, design capacity, current full capacity and battery health percentage

**General**
- Smooth animated rings, rounded cards and drawn vector icons
- Green (screen) and ruby (battery) accents with gold highlights, in dark and light themes
- Light / dark theme toggle in the header, remembered between launches
- Pull down to refresh (like in Chrome), refresh button, and automatic refresh when you switch tabs or return to the app
- Adaptive launcher icon with Android 13+ themed-icon support
- No internet permission, no ads, no analytics, no accounts

### How it works

| Data | Source | Root |
|---|---|---|
| Screen-on time, screen-off time, doze, discharge per state | `dumpsys batterystats --charged` | Yes |
| Cycle count, design capacity, full capacity | `/sys/class/power_supply/{battery,bms}/` | Yes |
| Level, status, plug type, health, technology, temperature, voltage | `ACTION_BATTERY_CHANGED` broadcast | No |
| Current now / average, charge counter | `BatteryManager` properties | No |

Root commands run in **one persistent `su` session**. Root managers such as Magisk or KernelSU therefore show their notification once when the first root command runs, not on every refresh. The values that change every second use only public Android APIs, so they never trigger root prompts.

Estimated capacity is calculated as `charge counter ÷ battery level`. Battery health is `current capacity ÷ design capacity`.

### Requirements

- Android 8.0 (API 26) or newer
- Root access (Magisk, KernelSU or similar) for the screen tab and for cycle count and capacity values. Without root, the battery tab's live values still work.

### Install

1. Download `app-release.apk` from the latest successful run in the repository's **Actions** tab (artifact `EkranSuresi-apk`), or build it yourself (below).
2. Install the APK. Android may ask you to allow installs from unknown sources.
3. Google Play Protect may warn about apps installed outside the Play Store or apps that use root. This is expected for self-distributed apps. Choose **More details → Install anyway**.
4. Open the app and grant root access when your root manager asks.

### Build

**With GitHub Actions (no PC needed)**

Every push runs `.github/workflows/build.yml` and produces an APK artifact. For a stable signature across updates, add your own signing key as repository secrets:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | Base64 text of your `.keystore` file |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key password |

Create a key once:

```bash
keytool -genkeypair -v -keystore release.keystore -alias myalias \
  -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Your Name"
```

Encode it for the secret:

```bash
# Linux / macOS / Termux
base64 -w0 release.keystore
# Windows PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore"))
```

Keep the keystore file and its password somewhere safe and **never commit them**. If you lose them you cannot update an installed app in place. Without these secrets the workflow still builds, but signs the APK with a temporary debug key.

**With Android Studio**

1. Open the project folder in Android Studio and let Gradle sync.
2. Run the app on a device, or use **Build → Build APK(s)**.

### Project structure

```
app/src/main/java/com/spatt/ekran/suresi/
├── MainActivity.kt   App layout, tabs and refresh logic
├── Components.kt     Rings, cards, icons, bottom bar, colours
└── Data.kt           Root shell, batterystats parser, battery readers
.github/workflows/build.yml   Cloud build and signing
```

Built with Kotlin and Jetpack Compose. Package name: `com.spatt.ekran.suresi`.

### Limitations and notes

- Battery values come from the manufacturer's kernel and firmware, so availability and units vary between devices. The app auto-detects whether a device reports current and charge in µA/µAh or mA/mAh. Fields a device does not report are shown as "—".
- The sign of the current is taken from the charging status, because manufacturers report current direction inconsistently.
- `batterystats` periods differ between Android versions and skins. On some devices the counters restart when the battery passes a high charge level after unplugging, not strictly at 100%.
- Cycle count and capacity files are readable only with root on most devices, and some devices do not expose them at all.
- Do not reset battery statistics from the command line if you rely on this app's numbers.

### Privacy

The app declares no permissions, does not use the network and stores only your light/dark theme choice on the device. Root is used only to run read-only commands (`dumpsys batterystats` and `cat` on battery files).

### Author

Developed by **Spatt**.

---

## Türkçe

### Nedir?

Bazı Android arayüzleri, sistem pil sayfalarında "ekran açık kalma süresi" bilgisini gizler. Oysa işletim sistemi bu veriyi tutmaya devam eder. **Ekran Süresi**, bu bilgiyi doğrudan Android'in `batterystats` servisinden okuyup sade, koyu temalı bir arayüzde gösterir. İkinci sekme ise akım, voltaj, sıcaklık, kapasite ve döngü sayısı gibi ayrıntılı pil bilgilerini sunar.

### Özellikler

**Ekran sekmesi (root gerekir)**
- Son tam şarjdan beri ekran açık kalma süresi (animasyonlu halka)
- Ekranın kaç kez açıldığı
- Pilde geçen süre, ekran kapalı süresi ve doze / sürekli açık ekran süresi
- Ekran açıkken ve kapalıyken harcanan pil (mAh ve %)
- Sistemin kendi *tahmini* ekran süresi, ölçülen değerle karışmasın diye ayrı bir kartta

**Pil sekmesi**
- *Canlı değerler, root gerekmez, her saniye yenilenir:* anlık akım (mA), güç (W), voltaj, sıcaklık, şarj durumu, güç kaynağı, pil sağlık durumu, teknoloji, ortalama akım, şarj sayacı, tahmini kapasite
- *Root ile okunanlar, sekmeye girince yenilenir:* döngü sayısı, tasarım kapasitesi, mevcut kapasite, pil sağlığı yüzdesi

**Genel**
- Akıcı animasyonlu halkalar, yuvarlak köşeli kartlar, çizimle yapılmış simgeler
- Koyu ve açık tema (üstteki düğmeyle değişir, seçiminiz hatırlanır); ekran için yeşil, pil için ruby vurgu, altın sarısı metinler
- Chrome'daki gibi aşağı çekerek yenileme, yenile düğmesi; sekme değişince ve uygulamaya dönünce de veriler tazelenir
- İnternet izni yok, reklam yok, veri toplama yok

### Nasıl çalışır?

Root komutları **tek bir kalıcı `su` oturumunda** çalışır. Magisk veya KernelSU bildirimi her yenilemede değil, ilk root komutunda bir kez çıkar. Saniyede bir yenilenen değerler yalnızca herkese açık Android API'lerini kullandığı için root bildirimi tetiklemez. Veri kaynakları için yukarıdaki tabloya bakın.

### Gereksinimler

- Android 8.0 (API 26) veya üzeri
- Ekran sekmesi ile döngü ve kapasite değerleri için root. Root olmadan da Pil sekmesindeki canlı değerler çalışır.

### Kurulum

1. Deponun **Actions** sekmesindeki son başarılı çalışmadan `EkranSuresi-apk` çıktısını indirin ya da kendiniz derleyin.
2. Zip'ten çıkan APK'yı kurun. "Bilinmeyen kaynaklardan yükleme" izni gerekebilir.
3. Play Protect uyarı verirse **Daha fazla bilgi → Yine de yükle** seçin. Mağaza dışından gelen ve root kullanan uygulamalarda bu normaldir.
4. Uygulamayı açıp root yöneticisinin isteğine izin verin.

### Derleme

Her `push` işleminde GitHub Actions bir APK üretir. Güncellemelerde imzanın değişmemesi için kendi anahtarınızı `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` ve `KEY_PASSWORD` gizli değişkenleri olarak ekleyin. Anahtar dosyasını ve şifresini yedekleyin, depoya **koymayın**. Android Studio ile derlemek için klasörü açıp Gradle senkronizasyonunu bekleyin ve **Build → Build APK(s)** seçin.

### Sınırlamalar

- Pil değerleri üreticinin çekirdeğinden ve yazılımından geldiği için cihazdan cihaza değişir. Cihazın vermediği alanlar "—" olarak görünür.
- Akımın yönü, üreticiler farklı raporladığı için şarj durumuna göre belirlenir.
- `batterystats` sayaçları sürüme ve arayüze göre farklı zamanlarda sıfırlanabilir.

### Gizlilik

Uygulama hiçbir izin istemez, ağa bağlanmaz ve cihazda yalnızca açık/koyu tema seçiminizi saklar. Root yalnızca salt okunur komutları çalıştırmak için kullanılır.

### Yazar

**Spatt** tarafından geliştirilmiştir.
