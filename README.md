# Volace

Android の音量プロファイル切り替えアプリ + ホーム画面ウィジェット。
広告なし・トラッキングなし・ネットワーク権限なしの、完全にオフラインで動く個人用ツールです。

> An ad-free volume profile switcher for Android, with home-screen widgets.
> Built as a replacement for Volume Ace, which no longer installs on modern devices.

## なぜ作ったか

音量プロファイル系のアプリはどれも広告付きで、参考にしていた **Volume Ace** は
新しい端末にインストールできなくなっていました。サイドロード前提なら Play ストアの
targetSdk 要件に縛られないので、必要な機能だけを自分で作ることにしたものです。

## できること

- **6つのストリームをまとめて切り替え** — 着信音 / 通知 / メディア / アラーム / 通話 / システム
- **着信モード** — 着信音 / バイブ / サイレント
- **プロファイルに色とアイコン** — 10色 × 14アイコン。一覧とウィジェットで同じ見た目になります
- **現在の音量を表示** — 端末の実際の音量（6ストリーム + 着信モード）をアプリとウィジェットに表示。
  音量キーや他アプリで変えても追従し、適用中のプロファイルとずれたら「変更あり」と表示します
- **ホーム画面ウィジェット3種**
  | サイズ | 動作 |
  |---|---|
  | 1×1 | タップするたびに次のプロファイルへ循環 |
  | 4×1 | 先頭4件を横一列 + 現在の音量（ミニバー）。タップで即適用 |
  | 4×2 | 現在の音量（バーと数値）+ 先頭8件を2段グリッド。タップで即適用 |

  4×1 / 4×2 の音量表示をタップすると、アプリのプロファイル一覧が開きます
- **アプリ内から「ホーム画面に追加」** — `requestPinAppWidget()` でウィジェットを直接配置できます
  （Pixel ランチャーのウィジェット検索はサイドロードしたアプリを索引しないため）
- **初回起動時に4プロファイルを自動生成** — 端末ごとの音量段階に合わせた値で

## 技術スタック

| 項目 | 選定 |
|---|---|
| 言語 / UI | Kotlin + Jetpack Compose (Material 3) |
| ウィジェット | 素の `RemoteViews` + `AppWidgetProvider` |
| 永続化 | Room |
| minSdk / targetSdk | 33 / 37 |

ウィジェットは当初 Jetpack Glance で実装しましたが、タップ後に再描画されない問題を
解決できなかったため、古典的な `RemoteViews` に書き直しています。
経緯は [DESIGN.md](DESIGN.md) の 5章・8.1 に記録しています。

## ビルド

```sh
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

複数のPCで開発するときは、共通のデバッグ鍵を `app/debug.keystore` に置いてください
（Google Drive の `Develop/volace/debug.keystore` に保管。`.gitignore` 済み）。
置いてあればそれで署名されるので、どのPCでビルドしても実機のアプリに上書きインストールできます。
無い場合は PC ごとの `~/.android/debug.keystore` で署名され、別のPCで入れたアプリには上書きできません
（`INSTALL_FAILED_UPDATE_INCOMPATIBLE`）。

テスト（Room のマイグレーション。JVM 上の Robolectric で動き、端末は不要）:

```sh
./gradlew testDebugUnitTest
```

端末に入れるビルドごとに `app/build.gradle.kts` の `versionCode` を上げてください。
DB のスキーマを変えるときは DESIGN.md 3章の「スキーマ変更のルール」に従ってください。

リリースビルドには署名設定が必要です。`keystore.properties.sample` をコピーして
自分のキーストア情報を書いてください（`keystore.properties` は `.gitignore` 済み）。

```sh
keytool -genkeypair -v -keystore ~/.android/volace-release.jks \
  -alias volace -keyalg RSA -keysize 4096 -validity 36500

cp keystore.properties.sample keystore.properties
# 中身を編集してから
./gradlew assembleRelease
```

設定ファイルが無い場合は署名設定を組み立てないだけで、ビルド自体は通ります。

## 権限

```xml
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
<uses-permission android:name="android.permission.ACCESS_NOTIFICATION_POLICY" />
```

`ACCESS_NOTIFICATION_POLICY`（サイレント モードへのアクセス）は宣言だけでは不十分で、
ユーザーが設定画面から個別に許可する必要があります。初回起動時に案内画面が出ます。

インターネット権限は持っていません。

## 実機で分かったこと

- **`STREAM_SYSTEM` は `STREAM_RING` にエイリアスされている**（Pixel / AOSP の audio policy）。
  独立に設定しても最後に書いた方が両方に反映されるため、適用順を固定しています
- **`RINGER_MODE_SILENT` を指定するとサイレント モード（DND）も有効になり、
  内部の ringer mode はバイブになる**。AOSP の `ZenModeHelper` の仕様で回避不可
- メディアの段階数は 0-25（着信音などは 0-7）。スライダーの上限は
  `getStreamMaxVolume()` から実行時に取得しています

詳細は [DESIGN.md](DESIGN.md) を参照してください。

## 動作確認済み端末

- Pixel 9 Pro XL (Android 17 / SDK 37)
- Pixel 11 Pro (Android 17 / SDK 37)
- Pixel 9a (Android 17 / SDK 37)

Play ストアには公開していません。自分と家族の端末へのサイドロード用です。
