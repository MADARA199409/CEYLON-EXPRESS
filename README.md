# Sea Cargo Sticker Maker (Android)

A native Android starter project for sea-cargo parcel labels.

## Included
- Cargo label form: sender/customer, phone, destination, tracking/reference, weight, package count
- Numbered labels (e.g. 1/5, 2/5...)
- Save/reuse customer details locally on the phone
- Export labels as a PDF using Android's built-in PDF renderer
- Bluetooth Classic printing to a paired thermal printer using generic ESC/POS text commands
- No account or cloud service required

## Important printer compatibility note
The printer brand/model is currently unknown. Bluetooth thermal printers do not all use the same protocol. This project uses **Bluetooth Classic SPP + generic ESC/POS**. If your printer uses Bluetooth LE, a proprietary protocol, or a different command language, printing may need a model-specific driver. Test with a small label first.

## Build an APK
This workspace does not contain the Android SDK or Gradle, so a compiled APK could not be produced here. To build:
1. Install Android Studio on a computer.
2. Open this folder (`SeaCargoStickerMaker`) in Android Studio.
3. Allow Gradle sync and install Android SDK Platform 35 if prompted.
4. Connect a phone or choose **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. Android Studio will show the output path, usually `app/build/outputs/apk/debug/app-debug.apk`.

For installation, enable permission to install apps from that source on your Android phone, then open the APK.

## Bluetooth setup
1. Pair the thermal printer in Android Settings first.
2. Open this app and grant Nearby devices / Bluetooth permissions.
3. Tap **Choose paired printer** and select the printer.
4. Use a small test label. If output is garbled or nothing prints, confirm the printer supports Bluetooth Classic SPP and ESC/POS, then adapt the print commands for its model.

## Label sizing
The PDF uses a 100 × 150 mm label page. The printer print path sends text commands and line feeds; paper size/cut behavior is printer-specific and may require tuning.

## Project details
- Package: `com.example.seacargosticker`
- Min SDK: 26 (Android 8.0)
- Target SDK: 35
