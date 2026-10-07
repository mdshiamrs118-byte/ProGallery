# Pro Gallery Lite

A super-light Android 10+ gallery written in Kotlin. Custom rounded popups, built-in photo editor,
albums, favorites, slideshow, search, and sorting, with a tiny footprint.

## What changed vs. the original Pro Gallery

| Area | Before | After |
| --- | --- | --- |
| Libraries | Glide, AppCompat, Material, ViewPager2, core-ktx, RecyclerView | **RecyclerView only** |
| Image loading | Glide | ~70-line loader (`Thumbs.kt`) using the system thumbnail cache + `ImageDecoder` |
| Build | Debug APK, no shrinking | Release APK with **R8 full mode + resource shrinking** |
| Launcher | Vector icon | Adaptive icon |
| Media reload | Every `onResume` | Only when MediaStore actually changes (ContentObserver) |
| Grid | Full rebind on every selection | Per-item updates, stable IDs, cached thumbnails |
| Editor memory | 8 undo steps at 2400 px | 5 undo steps at 2048 px |
| Delete | On the UI thread, failures ignored | Background thread, failures reported |

## Build

Push to GitHub and the **Android CI** workflow builds the release APK.
Download it from **Actions → latest run → Artifacts → `ProGallery-Lite-release-apk`**.
Push a tag like `v2.0` to also publish it as a GitHub Release.

The release APK is signed with the debug key so it installs directly. Swap in your own
keystore before publishing to an app store.

## Project layout

- `Data.kt` MediaStore queries, favorites, share, delete
- `Thumbs.kt` lightweight image loader and cache
- `MainActivity.kt` grid, albums, search, sort, multi-select
- `ViewerActivity.kt` pager, zoom, slideshow, details, wallpaper
- `EditorActivity.kt` adjust, filters, rotate/flip/crop
- `Ui.kt` custom rounded dialogs, sheets, toasts
