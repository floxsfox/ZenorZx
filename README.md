# zenorX

Manajer file Android (Kotlin + Jetpack Compose) dengan **dual panel kiri–kanan**, tampilan bergaya Ubuntu.

## Fitur
- Dual panel kiri–kanan (A | B), bisa dimatikan lewat ikon kolom di bar atas atau Pengaturan
- Tahan lama pada file lalu **seret** ke panel lain, atau ke sebuah folder (ada pilihan: tanya / langsung salin / langsung pindah)
- Salin, potong, tempel, hapus, ubah nama, folder & file baru, zip, ekstrak (zip/jar/apk), bagikan, buka dengan, properti
- Tombol cepat "Salin→B" / "Pindah→B" (atau →A) untuk transfer antar panel
- Multi-pilih, urutkan (nama/ukuran/tanggal/tipe), file tersembunyi, pencarian rekursif
- Editor teks bawaan, sidebar tempat ala Nautilus, tema gelap/terang

## Build lewat GitHub (dari ponsel)
1. Buat repo baru di GitHub.
2. Add file → Create new file → nama `.github/workflows/zenorx.yml`, isi dengan file `zenorx.yml`.
3. Add file → Upload files → unggah `zenorX.zip` ke root repo → Commit.
4. Tab Actions: workflow akan mengekstrak ZIP, commit hasilnya, lalu build APK.
5. Buka run yang selesai → Artifacts → `zenorX-debug-apk`.
