# Google Play Console 申告の回答案（下書き）

Play Console の「アプリのコンテンツ」などで聞かれることへの回答案。入力と提出は作者が行う。作成: 2026-10-05（issue #59）。
事実は `app/src/main/AndroidManifest.xml`、`DESIGN.md`（5.14〜5.19、6.1、8.7、8.11）、`PRIVACY.md` に基づく。
Play の要件は変わるので、入力のときに Console の質問文と見比べること。

## 1. すぐ答えられるもの

| 質問 | 回答 | 根拠 |
|---|---|---|
| プライバシーポリシーの URL | https://github.com/hong8300/volace/blob/main/PRIVACY.md | アプリの「設定」からも開ける（Play はアプリ内のリンクも求める） |
| 広告を含むか | いいえ | 広告 SDK なし |
| 広告 ID を使うか | いいえ | `AD_ID` 権限なし。統合 manifest で確認済み（6.1） |
| アプリのアクセス | 一部の機能が制限されている → 下の 2 の説明を入れる | 初回は「おやすみモードへのアクセス」の許可が要る |
| ターゲットの年齢層 | 18 歳以上（子ども向けではない） | 子ども向け（ファミリー）のポリシーを避けられる。13 歳以上でも差し支えない |
| 子どもにも魅力的か | いいえ | 音量の設定ツール |
| ニュースアプリか / 政府のアプリか / 金融機能 / 健康機能 | いいえ | |
| アカウントの削除 | 該当なし | アカウントがない |
| 有料か無料か | **有料**（あとから無料→有料に変えられない） | 価格は `listing.md` |
| 正確なアラーム（exact alarm）の申告 | **不要** | 申告が要るのは `USE_EXACT_ALARM`（時計・カレンダー系）。Volace は `SCHEDULE_EXACT_ALARM`（ユーザーが許可）なので対象外。ポリシーのページ（answer/16558241）で確認、2026-10-05 |
| 機密性の高い権限の申告（SMS・通話履歴・位置情報・全ファイルアクセスなど） | 対象なし | 権限は 6.1 のとおり。`WRITE_SETTINGS` と `ACCESS_NOTIFICATION_POLICY` は特別なアクセスで、ユーザーが設定画面で許可する |

### コンテンツのレーティング（IARC）

カテゴリは「ユーティリティ、仕事効率化、コミュニケーション、その他」。暴力、性的な内容、言葉、薬物、ギャンブル、ユーザー生成コンテンツの共有、位置情報の共有、
デジタル商品の購入（アプリ内課金なし）はすべて「いいえ」。見込みは全年齢（3+ / Everyone）。実際の結果は Console が出す。

### データセーフティ

| 質問 | 回答 |
|---|---|
| アプリは必須のユーザーデータを収集または共有するか | **いいえ** |
| 以降（暗号化、削除の手段、データの種類ごとの回答） | 該当なし（上が「いいえ」なら出ない） |

根拠: Play のヘルプでは、収集（collect）は「アプリからデータを端末の外へ送ること」で、端末内で処理するだけのデータは開示が要らない。
Volace は `INTERNET` 権限を持たず、何も送信しない（6.1）。Android の自動バックアップは `allowBackup="true"` で、DB（Bluetooth の機器名・アドレスを含む）が
ユーザー自身の Google アカウントに保存されうるが、アプリ側はそのデータを受け取らない。ヘルプには「ユーザーが自分のクラウドに保存し、
アプリが集めない場合は申告不要」とある。この点は `PRIVACY.md` に書いた。不安なら、`AndroidManifest.xml` の `allowBackup` を `false` にしてもよい
（その場合、新しい端末への移行はアプリの「バックアップ」ファイルで行う）。

## 2. アプリのアクセス（審査員向けの説明）

日本語:

```
ログインは不要です。初回起動時に「おやすみモードへのアクセス」の案内画面が出ます。
「設定を開く」を押し、一覧から Volace を選んでオンにしてから戻ると、すべての機能が使えます。
Bluetooth 連動は、ペアリング済みの Bluetooth 機器があるときだけ試せます（任意）。
```

English:

```
No login is required. On first launch the app shows a guide for "Do Not Disturb access".
Tap "Open settings", choose Volace in the list and turn it on, then go back: every feature is available.
Bluetooth switching can be tried only with a paired Bluetooth device (optional).
```

## 3. フォアグラウンド サービスの申告（アプリのコンテンツ → フォアグラウンド サービスの権限）

3 つのサービスはすべて `specialUse`。Console では種類ごとに「機能の説明」「処理が遅れたり中断されたときのユーザーへの影響」「機能を示す動画のリンク」を求められる。
使うユースケースは一覧に合うものがないので「その他（手入力）」。

**共通の理由（English）**:

```
The app changes the user's volume settings (ringer mode and six stream volumes) when the user asks for it:
at a time the user scheduled, when a timed profile ends, or when a Bluetooth device the user picked connects
or disconnects. Android 17 ignores volume changes from a background app unless a foreground service is
running (https://developer.android.com/about/versions/17/changes/bg-audio), and none of the predefined
foreground service types describes changing the user's volume settings. Each service shows a notification
while it runs and stops itself when the change is done. Nothing runs in the background all the time.
```

### 3.1 `TimerService`（時間指定）

| 項目 | 内容 |
|---|---|
| 開始契機 | ユーザーが「時間指定」で「1 時間だけサイレント」のように開始する（画面を見ている間） |
| 継続時間 | 決めた終了時刻まで（30 分〜3 時間、または指定した時刻）。終了すると止まる |
| 終了条件 | 終了時刻に元の音量へ戻したとき、「今すぐ戻す」を押したとき |
| 見え方 | 残り時間つきの通知。「今すぐ戻す」「30 分延長」ボタン |
| 遅れたり中断されたときの影響 | 終了時刻に音量が戻らず、サイレントのままになる（着信や通知を逃す） |

Description (English):

```
The user starts "apply this profile until a chosen time" (for example, silent for one hour). The service runs
until that time and shows a notification with the remaining time and buttons to go back now or extend by 30
minutes. At the end time it restores the previous volumes and stops. If it were deferred or killed, the phone
would stay silent after the time the user chose and the user could miss calls.
```

### 3.2 `ScheduleService`（スケジュール）

| 項目 | 内容 |
|---|---|
| 開始契機 | ユーザーが決めた時刻（例: 平日 22:00）に、正確なアラームが起動する |
| 継続時間 | 約 1 秒。切り替えが終わったら止まる |
| 終了条件 | 音量の切り替えと結果の確認が終わったとき |
| 見え方 | 通知（1 秒ほどで消える） |
| 遅れたり中断されたときの影響 | 決めた時刻に音量が切り替わらない（夜に大きな音のまま、朝にサイレントのまま）。切り替わらなかったときは通知で知らせ、タップで切り替える |

Description (English):

```
At a time the user scheduled (for example weekdays 22:00), an exact alarm starts this service, which switches
to the volume profile the user chose, checks the result and stops after about one second. If it were
deferred, the volumes would not change at the time the user chose; the app then shows a notification that
switches the profile when tapped.
```

### 3.3 `BluetoothService`（Bluetooth 連動）

| 項目 | 内容 |
|---|---|
| 開始契機 | ユーザーが登録した Bluetooth 機器（イヤホン・車）が接続・切断したとき |
| 継続時間 | 数秒（機器への音の出力先が切り替わるのを最大 8 秒待つ） |
| 終了条件 | 切り替えと結果の確認が終わったとき |
| 見え方 | 通知（数秒で消える） |
| 遅れたり中断されたときの影響 | 機器をつないでも音量が切り替わらない（車で大きな音が出るなど）。切り替わらなかったときは通知で知らせる |

Description (English):

```
When a Bluetooth device the user registered (earbuds or a car) connects or disconnects, this service switches
to the volume profile the user chose for that device. It waits up to eight seconds for the audio route to move
to the device, checks the result and stops. If it were deferred, the volumes would not match the device the
user connected; the app then shows a notification that switches the profile when tapped.
```

### 3.4 動画に映すこと（作者が撮る。限定公開の URL を Console に貼る）

各機能につき 1 本、1〜2 分。「ユーザーが操作して、サービスが動き、結果が出る」までを映す。画面の録画は `adb shell screenrecord` でも、端末の画面録画でもよい。

1. **時間指定**: プロファイルの「時間指定」→ 時刻を 2〜3 分先に指定 →「適用」。通知シェードを開いて残り時間の通知を映す → 終了時刻に音量が戻る（ウィジェットか一覧の「適用中」が変わる）
2. **スケジュール**: スケジュール画面でルールを 2〜3 分先に作る → ホーム画面に戻って待つ → 時刻に「前回」の表示とウィジェットが変わる。サービスの通知は 1 秒ほどなので、映らなくてよい（説明で補う）
3. **Bluetooth**: Bluetooth 画面で機器を選んでルールを作る → 機器をつなぐ → 「前回」の表示とウィジェットが変わる → 機器を外すと元に戻る

リリースビルドの時間指定は 30 分からなので、「時刻を指定…」で近い時刻にする（デバッグビルドには「1 分」がある）。

## 4. 権限の説明（審査で聞かれたときの答え）

| 権限 | 答え |
|---|---|
| `ACCESS_NOTIFICATION_POLICY` | 「サイレント」やおやすみモードを、プロファイルの切り替えで設定するため。ユーザーが設定画面で許可したときだけ働く。許可を求める前に、初回の案内画面で理由を説明している |
| `WRITE_SETTINGS` | プロファイルに選んだ着信音・通知音・アラーム音を標準の音として設定するため。音を選ぶプロファイルの編集画面でだけ案内する |
| `BLUETOOTH_CONNECT` | ペアリング済みの機器の一覧と、接続・切断を知るため。Bluetooth 画面を開いたとき実行時に求める |
| `SCHEDULE_EXACT_ALARM` | 時間指定の終了とスケジュールを時刻どおりに始めるため（アプリの中心機能の一つ）。ユーザーが設定画面で許可する。許可がないときの動作と案内は `DESIGN.md` 5.19 |
| `POST_NOTIFICATIONS` | 時間指定の表示と、切り替えられなかったときの知らせ。実行時に求める |
| `RECEIVE_BOOT_COMPLETED` | 再起動のあとに、時間指定とスケジュールのアラームを入れ直すため |
| `FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_SPECIAL_USE` | 3 の申告のとおり |

外から呼べる部品: `BluetoothReceiver` は Bluetooth スタックが別の uid で送るために exported で、送り手に `BLUETOOTH_CONNECT` を求めている（`DESIGN.md` 5.18）。

## 5. 公開までのチェックリスト

| 項目 | 担当 | 状態 |
|---|---|---|
| Play Console のアカウント・お支払いプロファイル（有料アプリに必要）・本人確認 | 作者 | |
| 署名鍵の方針（`DESIGN.md` 9.1: 手元の release 鍵をアプリ署名鍵として登録するか） | 作者 | **最初のアップロードの前に決める** |
| 価格・配布する国 | 作者 | `listing.md` |
| 連絡先メール（ストアに公開される） | 作者 | |
| 動画の撮影と URL（3.4） | 作者（撮影は支援できる） | |
| アプリアイコン・フィーチャーグラフィック・スクリーンショット | Claude が候補を作り、作者が選ぶ | `listing.md` |
| release の AAB（`bundleRelease`）と署名の確認 | Claude | |
| 公開候補のコミットで統合 manifest をもう一度確認 | Claude | 6.1 は 2026-10-05 時点 |
| 未確認の動作（許可なし・サービス停止中のスケジュール、再起動後、画面ロック中、Doze の Bluetooth、車） | Claude（再起動と車は作者の操作が要る） | `DESIGN.md` 8.11 |
| プライバシーポリシーの URL が開けること（`main` に `PRIVACY.md` がある） | 作者 | PR のマージ後 |
| 内部テストでの確認（Play 経由でインストールし、動作を確かめる） | 作者 | |
| 審査の提出と結果の記録（準備完了と審査結果を分けて #59 に残す） | 作者 | |
