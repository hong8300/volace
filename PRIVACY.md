# プライバシーポリシー / Privacy Policy

Volace（音量プロファイルの切り替えアプリ）
最終更新: 2026-10-05

日本語が正文です。英語は参考訳です。
The Japanese text is authoritative. The English text is a reference translation.

---

## 日本語

### 要点

- Volace は**ネットワーク権限（`INTERNET`）を持たず、データを外部に送信しません**。作者もほかの誰も、あなたのデータを受け取りません
- 広告、アクセス解析、クラッシュ収集、トラッキングは一切ありません
- アカウントも要りません

### アプリが端末の中で扱うデータ

次のデータは、すべてあなたの端末の中にだけ保存されます。

| データ | 使う目的 |
|---|---|
| プロファイル（名前、着信モード、音量、色、アイコン、おやすみモード、選んだ音） | 音量を切り替える |
| スケジュール（時刻、曜日、休む日、切り替え先） | 決めた時刻に切り替える |
| Bluetooth のルール（選んだ機器の名前とアドレス、切り替え先）と、直近の接続・切断の記録 | 機器をつないだとき・外したときに切り替える |
| 時間指定の状態（終了時刻、戻し先、開始前の音量） | 時間が来たら戻す |
| 見た目の設定（スキン、アイコンの種類）、ウィジェットとショートカットの状態 | 画面とウィジェットを描く |

### 使う権限と目的

| 権限 | 目的 |
|---|---|
| 音量の変更（`MODIFY_AUDIO_SETTINGS`） | 6 つのストリームの音量と着信モードを切り替える |
| おやすみモードへのアクセス（`ACCESS_NOTIFICATION_POLICY`） | 「サイレント」やプロファイルごとのおやすみモードを切り替える。あなたが設定画面で許可したときだけ有効 |
| システム設定の変更（`WRITE_SETTINGS`） | プロファイルで選んだ着信音・通知音・アラーム音を設定する。音を選ぶときだけ求める |
| 付近のデバイス（`BLUETOOTH_CONNECT`） | ペアリング済みの機器の一覧と、接続・切断を知る。Bluetooth 連動を使うときだけ求める |
| 通知（`POST_NOTIFICATIONS`） | 時間指定の表示と、切り替えられなかったときの知らせ |
| アラームとリマインダー（`SCHEDULE_EXACT_ALARM`） | 時間指定の終了とスケジュールを時刻どおりに始める。あなたが設定画面で許可したときだけ有効 |
| フォアグラウンドサービス（`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_SPECIAL_USE`） | 時間指定の間と、スケジュール・Bluetooth の切り替えの数秒だけ動かす。常駐しない |
| 起動の完了（`RECEIVE_BOOT_COMPLETED`） | 再起動のあとで、時間指定とスケジュールを入れ直す |

Bluetooth の機器の名前とアドレスは、あなたが選んだ機器を見分けるためだけに端末内に保存します。

### バックアップとほかの端末への移行

- アプリの「バックアップ」は、プロファイルの定義（名前、着信モード、音量、色、アイコン）をファイルに書き出します。保存先はあなたがシステムの画面で選び、アプリがそのファイルを送信することはありません
- Android の自動バックアップ（Google アカウントへのバックアップや、端末から端末への移行）が有効だと、Android が Volace のデータベース（プロファイル、スケジュール、Bluetooth のルールを含む）を、あなたの Google アカウントに保存することがあります。これは Android の仕組みによるもので、作者は受け取りません。Android の設定でオフにできます

### 第三者への提供

ありません。データはアプリの外に出ません。

### 子どもについて

Volace は子ども向けに作られていません。年齢にかかわらず、データを集めません。

### データの削除

アプリのデータを消す（Android の設定 → アプリ → Volace → ストレージ → データを削除）か、アプリを削除すると、端末内のデータはすべて消えます。作者の側には何も残っていません。

### リンク先

アプリの「設定」から、このポリシーとライセンス表示をブラウザで開けます。ブラウザでの閲覧は、ブラウザ側の規約とプライバシーポリシーに従います。

### 変更

このポリシーを変えるときは、このページを更新し、「最終更新」の日付を直します。変更の履歴は GitHub のコミット履歴で確認できます。

### 連絡先

[GitHub の Issues](https://github.com/hong8300/volace/issues) へ。

---

## English

### Summary

- Volace **has no network permission (`INTERNET`) and sends no data anywhere**. Neither the author nor anyone else receives your data.
- No ads, no analytics, no crash reporting, no tracking.
- No account is needed.

### Data the app handles on your device

All of the following stays on your device only.

| Data | Purpose |
|---|---|
| Profiles (name, ringer mode, volumes, color, icon, Do Not Disturb mode, chosen sounds) | Switching volumes |
| Schedule (times, days, days off, target profile) | Switching at the times you set |
| Bluetooth rules (name and address of the devices you pick, target profile) and a record of the latest connection / disconnection | Switching when a device connects or disconnects |
| Timed-profile state (end time, what to go back to, volumes before it started) | Going back when the time is up |
| Appearance settings (skin, icon style), widget and shortcut state | Drawing the screens and widgets |

### Permissions and what they are for

| Permission | Purpose |
|---|---|
| Change audio settings (`MODIFY_AUDIO_SETTINGS`) | Switch the volumes of six streams and the ringer mode |
| Do Not Disturb access (`ACCESS_NOTIFICATION_POLICY`) | Switch "Silent" and each profile's Do Not Disturb mode. Active only if you allow it in Settings |
| Modify system settings (`WRITE_SETTINGS`) | Set the ringtone, notification sound and alarm sound a profile chooses. Asked only when you pick a sound |
| Nearby devices (`BLUETOOTH_CONNECT`) | List paired devices and learn when they connect or disconnect. Asked only when you use Bluetooth switching |
| Notifications (`POST_NOTIFICATIONS`) | Show a timed profile, and tell you when a switch did not work |
| Alarms & reminders (`SCHEDULE_EXACT_ALARM`) | Start the end of a timed profile and the schedule on time. Active only if you allow it in Settings |
| Foreground services (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`) | Run during a timed profile and for a few seconds when the schedule or a Bluetooth device switches. Never always-on |
| Run at startup (`RECEIVE_BOOT_COMPLETED`) | Set timed profiles and the schedule again after a reboot |

The names and addresses of Bluetooth devices are stored on the device only to recognize the devices you picked.

### Backup and moving to another device

- The in-app "Backup" writes the profile definitions (name, ringer mode, volumes, color, icon) to a file. You choose where it goes in the system picker; the app never sends the file anywhere.
- If Android's automatic backup (to your Google Account, or device-to-device transfer) is on, Android may store Volace's database (including profiles, schedule and Bluetooth rules) in your Google Account. This is done by Android, not by the author, who receives nothing. You can turn it off in Android's settings.

### Sharing with third parties

None. Data never leaves the app.

### Children

Volace is not made for children. It collects no data regardless of age.

### Deleting data

Clear the app's data (Android Settings → Apps → Volace → Storage → Clear data) or uninstall the app, and all data on the device is gone. Nothing is kept on the author's side.

### Links

From the app's Settings you can open this policy and the license notices in your browser. Viewing them in the browser is governed by the browser's own terms and privacy policy.

### Changes

When this policy changes, this page is updated and the "Last updated" date is changed. The history is in the GitHub commit log.

### Contact

Please use [GitHub Issues](https://github.com/hong8300/volace/issues).
