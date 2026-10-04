# Volace

Android の音量プロファイル切り替えアプリ + ホーム画面ウィジェット。
広告なし・トラッキングなし・ネットワーク権限なしの、完全にオフラインで動く個人用ツールです。

> An ad-free volume profile switcher for Android, with home-screen widgets.
> Built as a replacement for Volume Ace, which no longer installs on modern devices.

ソースは公開していますが、**オープンソースではありません**。使えるのは「アプリの利用」と
「ソースからビルドして個人で使う」までです。詳しくは [ライセンスと利用条件](#ライセンスと利用条件) へ。

> The source is public, but this is **not open source**: use of the app, and building it from source
> for personal use, are allowed; modification and redistribution are not. See [LICENSE](LICENSE).

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
- **時間指定** — 「1 時間だけサイレント」のように、決めた時刻まで適用して自動で戻します（適用前の状態か、別のプロファイルへ）。
  実行中は通知に「今すぐ戻す」「30 分延長」。一覧の各行と、クイック設定タイルの選択画面から
- **バックアップ** — 全プロファイルを JSON ファイルに保存・復元（追加 / 置き換え）。別の端末へのコピーにも
- **アプリのショートカット** — アプリアイコンの長押しから、先頭4件のプロファイルを直接適用（ホーム画面に固定も可）
- **クイック設定タイル** — 通知シェードからプロファイルを選んで適用。アプリの「ウィジェット追加」から追加できます
- **アプリ内から「ホーム画面に追加」** — `requestPinAppWidget()` でウィジェットを直接配置できます
  （Pixel ランチャーのウィジェット検索はサイドロードしたアプリを索引しないため）
- **スキン** — 既定（ダーク）／ライト／端末に合わせる／Material You（壁紙の色）／ハイコントラスト／ミッドナイト。アプリとウィジェットの両方に反映
- **日本語・英語** — 端末の言語、または Android 13 のアプリ別の言語設定に従います
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

ソースからのビルドと、そのビルドを自分で使うことは [LICENSE](LICENSE) で許可しています
（改変と、ビルドしたものの再配布は許可していません）。

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

時間指定のために、フォアグラウンドサービス（`FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE`）、
正確なアラーム（`USE_EXACT_ALARM`）、通知（`POST_NOTIFICATIONS`、初回の時間指定のときに確認）、
再起動後の再開（`RECEIVE_BOOT_COMPLETED`）も使います。サービスは時間指定の間だけ動きます。

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

現在は Play ストアに公開していません。

## ライセンスと利用条件

条件は [LICENSE](LICENSE)（Volace License）にあります。ソースコード、アイコン等の素材、
ビルドしたアプリ（APK / AAB）のすべてが対象です。

**できること**

- ソースコードを読む
- アプリ Volace を使う
- このリポジトリのソースから自分でビルドして、個人で使う

**できないこと**（上に書いていないことは、すべて許可していません）

- 改変（個人で使うためのものも含む）
- 元の版・改変版を問わず、コピーの配布・再配布（無償でも有償でも）
- 販売・商用の再配布、第三者による Google Play などでの配布
- コードやアイコン等の素材を、ほかのソフトウェアに取り込むこと
- 自分でビルドしたものを他人に渡すこと

**補足**

- **GitHub:** 公開リポジトリは、GitHub の利用規約が認める範囲（GitHub 上での閲覧と fork）では
  LICENSE に制限されません。それ以外の利用は、fork 先でも LICENSE の範囲です
- **Google Play:** 作者自身が公式版を Google Play で有料配布することを目指しています（#59）。
  公式版の入手条件は、公開したときのストアの表示によります。ソースからのビルドは無償で、
  Play 版の購入とは関係ありません。第三者による配布はできません
- **第三者の素材・ライブラリ:** Material Icons や AndroidX など（アプリに入るものはすべて Apache License 2.0）は
  それぞれのライセンスに従います。表示とライセンス文は [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) にあります
