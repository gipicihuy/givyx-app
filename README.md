# Givy Downloader

Native Android app (Kotlin + Jetpack Compose, no WebView/Flutter/React
Native) with a clean, dark, modern UI:

- Input field untuk URL TikTok
- Tombol Download
- Status/progress download (indeterminate saat resolving, persentase saat
  mengunduh)
- Error handling yang jelas (pesan tampil langsung di kartu status)

## Arsitektur

Scraper dan downloader **sengaja dipisah total**, dihubungkan lewat satu
interface (`TikTokScraper` / `ScraperResult`) supaya kamu bisa
ganti/update logic scraping kapan saja tanpa menyentuh downloader atau UI.

```
app/src/main/java/com/givy/downloader/
├── MainActivity.kt              # UI (Compose) — input, tombol, status
├── viewmodel/
│   └── DownloadViewModel.kt      # State machine: Idle → Resolving → Downloading → Success/Error
├── scraper/
│   ├── TikTokScraper.kt          # ⬅️ TEMPEL SCRAPER KAMU DI SINI (class YourTikTokScraper)
│   └── ScraperResult.kt          # Kontrak output scraper → downloader
└── downloader/
    └── FileDownloader.kt         # Download murni dari URL, simpan ke MediaStore (Movies/Givy atau Music/Givy)
```

### Cara pasang scraper kamu

Buka `app/src/main/java/com/givy/downloader/scraper/TikTokScraper.kt`,
lalu isi body `YourTikTokScraper.resolve(tiktokUrl)`:

```kotlin
class YourTikTokScraper : TikTokScraper {
    override suspend fun resolve(tiktokUrl: String): ScraperResult {
        // panggil API / parsing HTML / dsb di sini
        return ScraperResult.Success(mediaUrl = "https://.../video.mp4")
        // atau, kalau gagal:
        // return ScraperResult.Error("pesan error yang jelas")
    }
}
```

Tidak ada file lain yang perlu diubah — `DownloadViewModel` sudah
memanggil `ScraperProvider.get()` yang mengarah ke class ini.

Kalau scraper kamu butuh API key/token/cookie, **jangan** hardcode di
file itu. Ikuti instruksi "Secrets" yang sudah ditulis sebagai komentar
di dalam `TikTokScraper.kt` (pakai `local.properties` + `BuildConfig`,
atau GitHub Secrets untuk CI).

## Build lokal

```bash
./gradlew assembleDebug
```

APK debug akan ada di `app/build/outputs/apk/debug/`.

## CI (GitHub Actions)

Workflow di `.github/workflows/build.yml` otomatis jalan setiap push/PR
ke `main` (atau manual lewat "Run workflow"):

1. Checkout project
2. Setup JDK 17 + Android SDK
3. `./gradlew assembleDebug`
4. Upload hasil APK sebagai artifact bernama `givy-downloader-debug-apk`

## Catatan

- Minimum SDK 24 (Android 7.0), target/compile SDK 34.
- Download disimpan lewat `MediaStore` (scoped storage-safe untuk
  Android 10+, fallback permission untuk Android 9 ke bawah).
- Tidak ada kode scraper TikTok di project ini — sesuai permintaan,
  hanya placeholder yang jelas dan aman untuk kamu isi sendiri.
