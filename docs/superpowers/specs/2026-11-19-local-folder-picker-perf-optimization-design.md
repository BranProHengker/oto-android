# Design: Local Folder Picker & System Performance Optimization

## 1. Intent & Context
Pengguna melaporkan penurunan performa (*overall performance drop*, aplikasi terasa berat di semua layar) setelah v1.2.2. Ditemukan bahwa aplikasi saat ini melakukan pemindaian otomatis terhadap seluruh file audio di perangkat (`MediaStore.Audio.Media`) saat izin storage diberikan, mencakup ribuan file audio acak (voice note, rekaman, ringtone) dan memicu penulisan metadata berulang yang membanjiri Room Database (`song` dan `sorted_song_artist_map`). Hal ini mengakibatkan loop rekomposisi di seluruh Flow observer UI. Selain itu, pengecekan migrasi database warisan (`migrateLegacyDatabase`) berjalan secara sinkron di thread utama setiap kali aplikasi dibuka tanpa flag permanen, serta eksekusi PRAGMA database menggunakan `query()` alih-alih `execSQL()`.

Tujuan dari perubahan ini adalah:
1. Mengubah pemindaian audio lokal menjadi sistem berbasis folder pilihan pengguna murni (*folder picker* via SAF `OpenDocumentTree`), menghentikan pemindaian seluruh perangkat secara global.
2. Menghilangkan bottleneck I/O dan loop rekomposisi agar performa aplikasi kembali ringan dan responsif di semua halaman.

## 2. Goals, Non-Goals, and Anti-Goals
- **Goals**:
  - Mengganti alur "Scan Phone" dengan "Pilih Folder Musik" di tab Local Library menggunakan `OpenDocumentTree`.
  - Hanya file audio di dalam folder yang dipilih pengguna yang di-scan dan dimasukkan ke library.
  - Menyediakan flag permanen (`DataStore`/`Preferences`) sehingga `migrateLegacyDatabase` hanya dijalankan sekali seumur hidup aplikasi.
  - Memperbaiki eksekusi PRAGMA Room Database menggunakan `db.execSQL()` agar mode WAL dan `synchronous = NORMAL` aktif efektif.
  - Mengamankan eksekusi background `LocalArtwork.repair` agar tidak memblokir atau membebani UI thread saat startup.
- **Non-Goals**:
  - Mengubah format database atau skema Room yang sudah berjalan.
  - Menghapus fitur import manual satu per satu file audio jika pengguna ingin memilih file spesifik.
- **Anti-Goals**:
  - Dilarang menjalankan scan MediaStore seluruh storage perangkat tanpa persetujuan eksplisit per-folder.
  - Dilarang menjalankan operasi disk blocking di thread utama pada `AppModule` atau `App.kt`.

## 3. Constraints
- Menjaga kompatibilitas data library pengguna yang sudah ada di `oto.db`.
- **STRICT**: Dilarang menjalankan build Gradle lokal (`./gradlew`) di mesin workstation. Semua verifikasi build dilakukan via GitHub Actions.
- Mengikuti arsitektur eksisting: Jetpack Compose, Room, Hilt, DataStore, SAF (`OpenDocumentTree`).

## 4. Approaches Considered
- **Pendekatan 1 (Dipilih Pengguna)**: Folder-Centric Murni + Engine Clean-Up.
  - Menonaktifkan `DeviceMusicScanner.scan()` phone-wide.
  - Mengarahkan tombol utama tab Local ke `folderPickerLauncher` (`OpenDocumentTree`) dan menampilkan daftar folder aktif.
  - Menambahkan *persistent flag* pada migrasi legacy database agar tidak mengeksekusi I/O sinkron di setiap *cold start*.
  - Mengganti `db.query("PRAGMA ...")` dengan `db.execSQL("PRAGMA ...")`.
  - *Tradeoff*: Perlu sedikit penyesuaian di `LibraryLocalTab` dan `AppModule`, tetapi 100% menuntaskan akar penyebab lag dan memberikan kontrol folder penuh kepada pengguna.
- **Pendekatan 2**: Minimal Folder Switch Saja.
  - Hanya mengubah tombol UI tanpa membersihkan trigger background dan PRAGMA SQLite.
  - *Tradeoff*: Cepat, namun performa aplikasi di halaman lain tetap berisiko terganggu oleh *cold start I/O* dan *synchronous disk locks*.
- **Pendekatan 3**: Hybrid Blacklist/Whitelist MediaStore.
  - Tetap melakukan scan seluruh perangkat lalu menyaring berdasarkan durasi atau nama folder.
  - *Tradeoff*: Tetap membebani CPU saat query awal dan pengguna tidak memiliki privasi folder mutlak.

## 5. Detailed Technical Design
### A. Database & Startup I/O Optimization (`MusicDatabase.kt` & `AppModule.kt`)
1. Tambahkan pengecekan flag `dataStore` / SharedPreferences `legacy_db_migrated`:
   - Jika sudah bernilai `true`, langsung *return* dari `migrateLegacyDatabase(context)`.
   - Jika migrasi selesai (atau tidak ada `mochy.db`), set flag menjadi `true`.
2. Perbaiki Room Callback:
   ```kotlin
   db.execSQL("PRAGMA busy_timeout = 60000")
   db.execSQL("PRAGMA cache_size = -16000")
   db.execSQL("PRAGMA wal_autocheckpoint = 1000")
   db.execSQL("PRAGMA synchronous = NORMAL")
   ```

### B. Local Folder-Centric Workflow (`LibraryLocalImport.kt` & `LibraryLocalTab.kt`)
1. Di `LibraryLocalImport.kt`:
   - Ubah aksi default `importFromPhone()` dan `scanPhone()` agar tidak memanggil `openAddMusic()` (yang mengeksekusi `viewModel.scanDevice()`).
   - Ubah agar memanggil `actions.pickFolder()`.
2. Di `LibraryLocalTab.kt`:
   - Ubah kartu aksi: Tombol utama menjadi "Pilih Folder Musik" / "Tambah Folder Musik" yang langsung meluncurkan `OpenDocumentTree`.
   - Tampilkan daftar folder yang sedang dipantau melalui `LocalFolders.read(context)`. Pengguna dapat menambah folder baru atau menghapus folder yang tidak diinginkan.

### C. Background Tasks Optimization (`App.kt` & `LibraryImportViewModel.kt`)
1. Di `App.kt`:
   - Pastikan `LocalArtwork.repair` hanya dijalankan jika ada indikasi cover rusak, dan diberi jeda (*delay*) agar tidak berebut resource disk saat frame awal Compose sedang me-render Home.
2. Di `LibraryImportViewModel.kt`:
   - `scanDevice()` dinonaktifkan dari auto-trigger.

## 6. Verification Plan
1. Verifikasi sintaks dan kompilasi static tanpa menjalankan Gradle lokal.
2. Commit dan push ke branch `main`, tag rilis baru (atau pantau via CI).
3. Pastikan alur GitHub Actions sukses memvalidasi dan mem-build APK rilis.
