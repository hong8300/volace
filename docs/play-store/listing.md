# Google Play ストア掲載情報（下書き）

Play Console の「ストアの掲載情報」に貼る文面と、アセットの条件。下書きなので、貼る前に作者が読んで直すこと。
作成: 2026-10-05（issue #59）。事実は `README.md`・`DESIGN.md`・`PRIVACY.md` と、実機の確認結果に基づく。

- 文字数の上限は Play の現行ヘルプ（2026-10-05 時点）による。タイトルは 30 文字、短い説明は 80 文字、詳しい説明は 4000 文字
- **「確実に動く」とは書かない**。Android 17 の制限で、切り替えられないことがある（`DESIGN.md` 8.7）。切り替えられなかったときは通知で知らせる、と書く
- 確認済みの端末は Pixel 9 Pro XL / Pixel 11 Pro / Pixel 9a（いずれも Android 17）だけ。そのまま書いて、返金につながる期待のずれを減らす

## 基本情報

| 項目 | 内容 |
|---|---|
| アプリ名 | 日本語（既定）: `Volace：音量プロファイル`（15 文字） / English: `Volace: Volume Profiles`（23 文字） |
| 種類 | アプリ（ゲームではない）、**有料**（あとから無料→有料には変えられない。価格は下の「価格と配布」） |
| カテゴリ | ツール |
| 連絡先メール | （作者が入力。ストアに公開される） |
| ウェブサイト | https://github.com/hong8300/volace |
| プライバシーポリシー | https://github.com/hong8300/volace/blob/main/PRIVACY.md （アプリの「設定」からも開ける。Play はアプリ内のリンクも求める） |
| 既定の言語 | 日本語（`values/strings.xml`）。英語（`values-en`）も用意 |

## 短い説明（80 文字まで）

日本語（48 文字）:

```
音量をワンタップで切り替え。ウィジェット、スケジュール、Bluetooth連動。広告・通信なし。
```

English:

```
One-tap volume profiles with widgets, schedule and Bluetooth. No ads or network.
```

## 詳しい説明（4000 文字まで）

### 日本語

```
Volace は、着信音・通知・メディア・アラーム・通話・システムの 6 つの音量と着信モードを、「プロファイル」としてまとめて切り替えるアプリです。

広告なし。トラッキングなし。ネットワーク権限なし。データは端末の外に出ません。

■ できること
・6 つの音量をまとめて切り替え。着信モード（着信音／バイブ／サイレント）も
・プロファイルごとに色とアイコン（28 色、30 種類）。かわいい絵文字のアイコンにも切り替え可
・ホーム画面ウィジェット 3 種（1×1、4×1、4×2）。現在の音量も表示
・クイック設定タイルと、アプリアイコン長押しのショートカット
・時間指定：「1 時間だけサイレント」のように、決めた時刻まで適用して、自動で戻します
・スケジュール：「平日の 22:00 にマナー、7:00 に通常」。祝日や休暇は「休む日」に
・Bluetooth 連動：イヤホンや車をつないだら切り替え、外したら戻します
・おやすみモード（重要な通知のみ／アラームのみ）と、着信音・通知音・アラーム音もプロファイルごとに
・スキンを多数用意（ダーク、ライト、Material You など）
・バックアップ：プロファイルをファイルに保存・復元
・日本語・英語

■ 必要な許可
・おやすみモードへのアクセス（必須）：サイレントなどに切り替えるため。初回に案内が出ます
・通知：時間指定の表示と、切り替えられなかったときの知らせ
・アラームとリマインダー：時間指定の終了とスケジュールを、時刻どおりに始めるため
・付近のデバイス：Bluetooth 連動を使うとき
・システム設定の変更：プロファイルで着信音などを選ぶとき
どの許可も、必要になった画面で理由を説明します。

■ 知っておいてほしいこと
・Android 17 では、アプリがバックグラウンドから音量を変えることが制限され、条件を満たさないと Android が黙って無視することがあります。Volace は切り替えたあとで音量を読み直し、切り替えられなかったときは通知でお知らせします。通知をタップすると切り替わります
・動作確認をしている端末は、Pixel 9 Pro XL、Pixel 11 Pro、Pixel 9a（いずれも Android 17）です。ほかの端末や Android のバージョンでは確認していません
・ほかのアプリが独自に決めている通知音や、時計アプリのアラームごとの音は変わりません
・省電力や「アラームとリマインダー」の許可がないときは、時間指定やスケジュールが遅れることがあります

■ プライバシー
Volace はデータを送信しません。プロファイル、スケジュール、Bluetooth の機器名などは端末の中にだけ保存します。アプリの「設定」からプライバシーポリシーを読めます。

■ ソースコード
ソースコードは GitHub で公開しています。使うことと、ソースからビルドして個人で使うことはできます。改変や再配布はできません（オープンソースではありません）。詳しくは LICENSE をご覧ください。
https://github.com/hong8300/volace
```

### English

```
Volace switches the volumes of six streams (ringtone, notifications, media, alarm, calls, system) and the ringer mode together, as a "profile".

No ads. No tracking. No network permission. Your data never leaves the phone.

WHAT IT DOES
- Switch six volumes at once, plus the ringer mode (ring / vibrate / silent)
- A color and an icon for each profile (28 colors, 30 icons), or cute emoji icons
- Three home-screen widgets (1x1, 4x1, 4x2) that also show the current volumes
- A Quick Settings tile and launcher shortcuts
- Timed profiles: "silent for one hour", then it goes back by itself
- Schedule: "weekdays 22:00 Vibrate, 7:00 Normal", with days off for holidays
- Bluetooth: switch when earbuds or a car connect, and switch back when they disconnect
- A Do Not Disturb mode (priority only / alarms only) and ringtone, notification and alarm sounds for each profile
- Many skins (dark, light, Material You and more)
- Backup: save and restore your profiles as a file
- Japanese and English

PERMISSIONS
- Do Not Disturb access (required): to switch to Silent and similar profiles. A guide appears on first launch
- Notifications: to show a timed profile and to tell you when a switch did not work
- Alarms & reminders: to start timed profiles and the schedule on time
- Nearby devices: only when you use Bluetooth switching
- Modify system settings: only when you pick a ringtone or other sound in a profile
Each permission is explained on the screen where it is needed.

GOOD TO KNOW
- Android 17 restricts changing volumes from the background, and Android may silently ignore a change when its conditions are not met. Volace reads the volumes back after switching and tells you with a notification when a switch did not work. Tap the notification to switch.
- Tested on the Pixel 9 Pro XL, Pixel 11 Pro and Pixel 9a (all on Android 17). Other devices and Android versions are not tested.
- Notification sounds that other apps set on their own, and per-alarm sounds in clock apps, do not change.
- Without power-saving exemptions or the "Alarms & reminders" permission, timed profiles and the schedule can come late.

PRIVACY
Volace sends no data. Profiles, schedules and Bluetooth device names stay on the phone. You can read the privacy policy from Settings in the app.

SOURCE CODE
The source code is public on GitHub. You may use the app, and build it from source for your own personal use. Modification and redistribution are not allowed (this is not open source). See LICENSE.
https://github.com/hong8300/volace
```

## 最近の更新内容（リリースノート。500 文字まで）

初回公開（日本語）:

```
初回公開です。
音量プロファイルの切り替え、ホーム画面ウィジェット、クイック設定タイル、時間指定、スケジュール、Bluetooth 連動、スキン、バックアップに対応しています。
```

First release (English):

```
First release.
Volume profiles, home-screen widgets, a Quick Settings tile, timed profiles, a schedule, Bluetooth switching, skins and backup.
```

## 価格と配布

| 項目 | 案 | 備考 |
|---|---|---|
| 基準価格 | 200 円 | 価格は後から変えられる。**無料で出すと有料には戻せない**ので、最初から有料にする |
| 他の国 | 基準価格から自動換算。米国などは 1.49 / 1.99 ドルなど切りのよい値に手で直す | Console の国別プレビューで確認する。税は既定で上乗せ（税込みにもできる）。日本の消費税は Google が徴収・納付する |
| 手数料 | 年間売上 100 万ドルまでは 10%（日本は 2026-09-30、米国は 2026-06-30 から） | 公式ヘルプ（answer/16954621）による。手取りは 200 円（税抜き）で約 180 円、税込みで約 164 円 |
| 配布する国 | 作者が選ぶ | 画面は日本語・英語だけ。ほかの言語圏に出すと、読めないことによる低評価が増えうる |
| 端末 | 既定（Android 13 以上） | `minSdk` 33。確認済みは Pixel の Android 17 のみ（説明文に書いた） |

## 画像などのアセット

| 項目 | 条件 | 状態 |
|---|---|---|
| アプリアイコン | 512×512 の 32 ビット PNG（アルファ付き）、1024KB まで | **未作成**。`ic_launcher_*` のベクターから書き出す |
| フィーチャーグラフィック | 1024×500 の JPEG か 24 ビット PNG（アルファなし） | **未作成** |
| スマートフォンの画面 | 2〜8 枚。JPEG か 24 ビット PNG（アルファなし）。各辺 320〜3840px で、**長い辺は短い辺の 2 倍まで** | **未作成**。Pixel 9a の実画面は 1080×2424（2.24 倍）で条件を超えるので、1080×2160 などに切り出す。プロモーションの対象にするには(4 枚以上)、縦なら 1080×1920 以上（9:16） |

載せる画面の候補（上から優先）:

1. プロファイル一覧（現在の音量カードとスケジュール・Bluetooth のカード）
2. ホーム画面のウィジェット（4×1 と 4×2）
3. プロファイルの編集（音量スライダー、色、アイコン）
4. 時間指定のダイアログ
5. スケジュール画面
6. Bluetooth 画面
7. スキンの選択（かわいいアイコンの丸い形）
8. クイック設定タイルの選択画面

見た目は候補を何通りか作ってから選ぶ（フィーチャーグラフィックの構図、スクリーンショットのフレームの有無）。
