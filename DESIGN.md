# Volace — Android Volume Control App + Widget 設計書

## 0. 背景・ゴール
- 既存の Volume Profile 系アプリ(Volume Ace 等)は広告付き、かつ新端末にインストール不可
- 自分専用に、6ストリームのプロファイル切り替えができるアプリ+ウィジェットを作る
- 対象端末: Google Pixel 9 Pro XL / Google Pixel 11 Pro(自分・妻用、計2台、サイドロードのみ)
- Play Store には公開しない(将来公開する可能性はあるが、初期スコープ外)

## 1. スコープ(MVP)
含む:
- プロファイル(名前 + 6ストリームの音量値)の作成・編集・削除・並び替え
- プロファイル一覧からタップで即時適用
- ホーム画面ウィジェットからプロファイルをワンタップ適用
- 初回起動時の DND(Do Not Disturb)アクセス許可オンボーディング

含まない(将来検討):
- プロファイルごとのカスタム着信音選択(RingtoneManager 連携) — 手間の割に優先度低いため見送り
- 時間帯・Wi-Fi・位置情報による自動プロファイル切替 — 要望に含まれていないため見送り
- Play Store 公開対応(署名・課金なし表記・プライバシーポリシー等)

## 2. 技術スタック
| 項目 | 選定 | 理由 |
|---|---|---|
| 言語 | Kotlin | 標準 |
| UI(本体アプリ) | Jetpack Compose (Material3) | 2026年時点の標準、スライダーUIと相性が良い |
| ウィジェット | RemoteViews + AppWidgetProvider | 当初は Jetpack Glance だったが、タップ後に再描画されない問題で置き換えた(5.1 / 8.1) |
| 永続化 | Room | プロファイルのCRUD・並び替えに向く。件数は数個〜十数個想定でオーバースペックにならない |
| 非同期 | Kotlin Coroutines + Flow | Room/Composeとの親和性 |
| minSdk | 33 (Android 13) | 対象端末2台のみ・自己配布のため後方互換を切り捨てて簡素化 |
| compileSdk | 37(Android 17) | 最新の API を使う |
| targetSdk | 36(Android 16) | 37 にすると、バックグラウンドからの音量変更に「ユーザー操作から始めた FGS」が要り、スケジュール(5.15)の自動切り替えができない(8.7)。サイドロードのためストアの targetSdk 要件は無関係 |
| 署名 | debug鍵 or 自己管理のrelease鍵 | 2台への配布のみなので簡易でよいが、上書きアップデートを繰り返すなら鍵は固定して保管 |

## 3. データモデル

```
Profile
  id: Long (PK, autogenerate)
  name: String
  orderIndex: Int          // 並び替え用
  ringerMode: Int           // AudioManager.RINGER_MODE_NORMAL / VIBRATE / SILENT
  ringVolume: Int           // STREAM_RING
  notificationVolume: Int   // STREAM_NOTIFICATION
  mediaVolume: Int          // STREAM_MUSIC
  alarmVolume: Int          // STREAM_ALARM
  voiceCallVolume: Int      // STREAM_VOICE_CALL
  systemVolume: Int         // STREAM_SYSTEM
  isActive: Boolean         // 直近に適用したプロファイル(一覧のハイライト表示用)
  keepMask: Int             // 「変更しない」ストリーム(ビット列。v3〜)
  colorArgb: Int            // アクセントカラー(一覧・ウィジェット共通)
  iconKey: String           // ProfileIcon のキー。未知の値は既定アイコンにフォールバック
  ringtoneUri: String?      // 標準の着信音・通知音・アラーム音(v5〜)。null = 変更しない、"" = なし(5.16)
  notificationSoundUri: String?
  alarmSoundUri: String?
  dndMode: Int              // おやすみモード(v6〜)。0 = 使わない / 1 = 重要な通知のみ / 2 = アラームのみ(5.17)
```

Room の schema version は **6**。v1 → v2 で `colorArgb` / `iconKey`、v2 → v3 で `keepMask`、v4 → v5 で音の 3 列、v5 → v6 で `dndMode` を `ALTER TABLE ADD COLUMN` し、
v3 → v4 でスケジュールの表(5.15)を作るマイグレーションを持つ(v1〜v3 は実機で既存データを保持したまま移行できることを確認済み)。

`keepMask`(issue #21)はストリームごとの「変更しない」。1ストリーム1ビット(`VolumeStream.keepBit`、DB に保存されるので番号を変えない)。
立っているストリームは適用時に書き込まず、「変更あり」の判定からも外す。音量の値は残すので、スイッチを戻せば元の値で適用される。
編集画面の各音量に「この音量は変更しない」スイッチ、一覧のミニバーは空のバー、バックアップは `"keep": ["media", ...]`(古いファイルは「なし」扱い)。

**スキーマ変更のルール(issue #8)**
- `fallbackToDestructiveMigration` は**使わない**。以前は併用していたが、マイグレーションを書き忘れたとき
  (や古い APK を新しい DB の上に入れたとき)に全プロファイルが黙って消え、`MainActivity` が既定の4件で
  埋め直すため、消えたことにも気づけなかった。今は開けずにクラッシュする(データは残る)
- スキーマは `app/schemas/` に出力してコミットする(`exportSchema = true`、KSP の `room.schemaLocation`)。
  `1.json` は当時出力していなかったので、`2.json` から v2 で追加した2列を除いて復元したもの
- version を上げるときは、`VolaceDatabase.MIGRATIONS` に追加 → 新しい JSON をコミット → `MigrationTest` に追加
- `MigrationTest` は Robolectric(JVM)で動く。Robolectric は API 35 以降に Java 21 が要り、ビルドは JDK 17 なので
  SDK 34 で動かす(`app/src/test/resources/robolectric.properties`)。Robolectric はアプリ本体のアセットしか読まないため、
  スキーマは debug ビルドのアセットに含めている(リリースには入らない)
- 実機の instrumentation テスト(`connectedAndroidTest`)は終了時にアプリをアンインストールし、データとウィジェットが消えるので、
  普段使いの端末では実行しない

初回起動時(`count() == 0`)に `DefaultProfiles` が「通常 / マナー / サイレント / 音楽」の4件を生成する。
値は端末ごとの `getStreamMaxVolume()` に対する比率で決めるため、機種差を吸収できる。

各ストリームの最大値は端末実行時に `AudioManager.getStreamMaxVolume(streamType)` で取得し、スライダーの range に反映(端末・Android バージョンによって最大値が異なるためハードコード不可)。

## 4. 画面設計

### 4.1 プロファイル一覧 (ProfileListScreen)
参考: 添付画像1
- 各行は角丸カード。左に **色付きアイコンチップ**、中央にプロファイル名と着信モード、右に **6本のミニバー**と編集ボタン
- 各行の下に **「編集」「適用」の文字ボタン**(issue #17)。以前は行全体のタップで即時適用していたが、
  内容を見るつもりのタップで音量が変わるので、カード本体はタップしても何もしない。
  「適用」は適用中なら「適用中」(押せない)、ずれていれば「再適用」になる。
  適用中の行は「プロファイル色の薄い塗り + 同色の枠 + 『適用中』バッジ」で強調
- ミニバーは **各ストリーム自身の最大値で正規化**する。プロファイル内の最大値で正規化すると
  着信音 7/7 がメディア 12/25 より短く見えてしまうため(初期実装のバグ)。バーの下に `R N M A V S` のラベルを表示
- **下部バー**に「ホーム画面へ」「ウィジェット追加」「プロファイル追加」の3つを、アイコン + 文字で等幅に並べる。
  「プロファイル追加」だけ色付きにして主操作と分かるようにした(issue #3)。
  当初は右下 FAB(「追加」)と上部バーのアイコンだけのボタンだったが、何のボタンか分からないという指摘を受けて変更
- 「ホーム画面へ」は `ACTION_MAIN` + `CATEGORY_HOME` でランチャーを開く。
  ウィジェットからアプリを開く使い方(5.5)が主なので、元のホーム画面へ戻る手段を明示した
- 一番上に **「現在の音量」カード**(5.5)。端末の実際の着信モードと6ストリームの値をバーと数値で表示し、
  音量キー等で変わるとその場で更新される(`VOLUME_CHANGED_ACTION` 等の動的レシーバ、150ms まとめて読む)
- 適用中のプロファイルと端末の音量がずれたら(5.7)、その行は塗りを外して枠線だけにし、
  バッジを「適用中」→ **「変更あり」** に変える。カードにも「『○○』の適用後に変更されています」と出す
- 並べ替えは上部バーの「並べ替え」(並べ替え中は「完了」)でモードを切り替え、各行に「上へ」「下へ」が出る方式
  (先頭の「上へ」・末尾の「下へ」は押せない)。
  常時表示しないのは、行全体をタップ領域として最大化し誤タップを避けるため

### 4.2 プロファイル編集 (ProfileEditScreen)
参考: 添付画像2
- 並び順は **名前 → 着信モード → 音量 → 色 → アイコン**(issue #18。音量を変えに来たのに色・アイコンを先に通らなくてよい)
- 着信モードの下に「何が鳴るか」を常に表示する(「サイレント」は「すべて無音」と誤解されやすいので、
  メディア・アラーム・通話は鳴ることを書く)。そのモードで鳴らなくなる行(着信音・通知・システム)には
  「このモードでは鳴りません」、システムには「着信音と連動」と注記する
- 「現在の着信モードと音量を取り込む」の後に Snackbar で「元に戻す」を出す
- 上部バーの「複製」: 画面に表示中の内容で新しい(未保存の)プロファイルになり、名前は「○○のコピー」。
  保存すると末尾に追加される(元のプロファイルは変わらない)
- 上部: アイコンチップ + 名前入力
- **色**: 10色から選択(横スクロール)
- **アイコン**: 14種から選択(横スクロール)。一覧・ウィジェットの両方に反映される
- **着信モード**: 着信音 / バイブ / サイレント の3ボタン(= `setRingerMode()` のショートカット。
  これは Ring/Notification に連動する **端末全体のモード**であり、ストリームごとの独立設定ではないため、
  個別行にはvibrateアイコンを付けずここに集約)
- **音量**: 「現在の端末の音量を取り込む」ボタン + 6ストリームのスライダー。
  各行に −/+ ボタンを付けて1段階ずつの微調整をしやすくしている
- スライダーは `AudioManager.getStreamMaxVolume()` を上限に設定
- 適用中のプロファイルを編集しているときは、保存ボタンが **「保存して適用」** になり、保存後に端末へ再適用する(issue #9)。
  「変更あり」は DB の値と端末の音量を比べて決めるので、保存だけだと保存した瞬間に「変更あり」になっていた。
  適用中かどうかは開いた時点のコピーではなく DB を監視して判断する(編集中にウィジェットで切り替わることがある)。
  保存・削除の結果は一覧に戻ってから Snackbar で表示する
- 上部バーに 戻る(←) / 削除、**下部バーに「キャンセル」「保存」**(issue #3。以前は上部バーの ✓ だけで保存と分かりにくかった)
- 戻る(ジェスチャー・←・キャンセル)は一覧に戻る。未保存の変更があれば「変更を破棄しますか？」を確認する。
  以前は `BackHandler` が無く、システムの戻るでアクティビティごと閉じて編集内容が黙って消えていた
- 着信音選択アイコン(画像にある音符アイコン)はMVP対象外のため省略

### 4.3 初回オンボーディング (OnboardingScreen)
- `NotificationManager.isNotificationPolicyAccessGranted()` が false の場合に表示
- 「Ring/Notification 音量を操作するには DND アクセス許可が必要」と説明
- ボタンで `Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS` を起動
- `onResume()` で再チェックし、許可されたらメイン画面へ

## 5. ウィジェット設計

参考: 添付画像3(複数レイアウト案あり)

**方針: ウィジェット内でのドラッグ操作は行わない。** ウィジェットの対話要素は基本的に「タップ→PendingIntent」のみで、ドラッグ可能なスライダーは実質サポートされていないため、本体アプリのスライダーUIとは役割を分ける。

### 5.1 実装方式: Glance をやめ、古典的な RemoteViews + AppWidgetProvider へ

当初 Jetpack Glance で実装したが、**タップ後にウィジェットの表示が更新されない**問題が実機で再現し解決できなかった
(`updateAll()` / `update()` / 生の `AppWidgetManager.updateAppWidget()` のいずれも例外なく完了するのに再描画されない。
Glance のセッションが自前の RemoteViews を保持し続けているのが原因と推定)。

そのため **Glance 依存を削除**し、以下の古典的構成へ全面的に書き換えた。

```
AppWidgetProvider (VolaceWidgetProvider) を継承した3つの provider
  ├ Volace1x1Provider  … WidgetStyle.SINGLE (1×1)
  ├ Volace1x4Provider  … WidgetStyle.ROW4   (4×1)
  └ Volace2x4Provider  … WidgetStyle.GRID8  (4×2)

VolumeApplyReceiver (BroadcastReceiver)
  ← ウィジェットの各セルの PendingIntent が飛んでくる唯一の受け口
  → VolumeApplier.apply() + dao.applyActive() → WidgetRefresher.refreshAll()

WidgetRefresher.refreshAll()
  → 3つの provider すべてについて getAppWidgetIds() を引き、
    配置済みのすべてのウィジェットを updateAppWidget() で描き直す
```

- どのサイズのウィジェットを押しても、**配置済みの全ウィジェットが一斉に更新される**
- DBアクセスが必要なため `goAsync()` + `Dispatchers.IO` のコルーチンで処理し、完了時に `PendingResult.finish()`
- アプリ側でプロファイルを変更/削除/並べ替えした場合も `WidgetRefresher.request()` で同じ経路を通る

### 5.2 サイズバリエーション

| provider | レイアウト | セル数 | 動作 |
|---|---|---|---|
| 1×1 | `widget_1x1.xml` | 1 | 現在のプロファイルを表示。**タップで次のプロファイルへ循環** |
| 4×1 | `widget_1x4.xml` | 4 | 先頭4件を横並び。タップで即適用 |
| 4×2 | `widget_2x4.xml` | 8 | 先頭8件を2段グリッド。タップで即適用 |

- 表示されるのは一覧の並び順(orderIndex)の先頭から。アプリの「並べ替え」で調整する
- プロファイルがセル数に満たない場合: 1段ものは余りセルを `GONE`(残りが広がる)、2段ものは `INVISIBLE`(列が揃う)。4件以下なら2段目の行ごと `GONE`
- ランチャーのウィジェット選択画面で3種を見分けられるよう、`android:label` / `android:description` / `previewLayout` を個別に設定

### 5.3 セルの見た目(どのプロファイルか見分けられるように)

Profile に **accent color (`colorArgb`)** と **アイコン (`iconKey`)** を持たせ、アプリの一覧とウィジェットで同じ見た目にした。

| 状態 | 背景 | アイコン | 文字 | 枠 |
|---|---|---|---|---|
| 適用中 | プロファイル色 100% | 白 | 白 | 白リング |
| 非適用 | プロファイル色 約19% | プロファイル色を明るくした色 | 薄いグレー | なし |

- 実装上は「白い角丸 shape」の `ImageView` を1枚敷き、`RemoteViews.setInt(id, "setColorFilter", argb)` と
  `setImageAlpha` で色と濃度を出している。色ごとに drawable を用意する必要がない
- プロファイルが0件のときは「追加」セルを1つ出し、タップでアプリを開く

### 5.4 アプリ内からの「ホーム画面に追加」

**Pixel ランチャーのウィジェット検索はサイドロードしたアプリを索引しない**(実機で確認。
「Volace」「vol」で検索しても0件だが、アプリアイコン長押し →「ウィジェット」からは正しく3種が出る)。
これでは発見しづらいので、アプリの下部バーに「ウィジェット追加」ボタンを置き、
`AppWidgetManager.requestPinAppWidget()` でシステムの「ホーム画面に追加」ダイアログを直接出せるようにした。
1×1 / 4×1 / 4×2 をその場で選べる。

### 5.5 現在の音量の表示(issue #1)

「音量キーや他のアプリで音量を変えても、ウィジェット上は適用中のままで変わったことが分からない」
「ウィジェットから音量の詳細を見たい・アプリを開きたい」への対応。

| ウィジェット | 表示 | タップ |
|---|---|---|
| 1×1 | 名前の下に「タップで次へ」/「変更あり」/「未適用・タップで適用」(issue #16) | 循環。**横に2マス以上広げると「選ぶ」タイル**が増え、タップでタイルと同じ選択画面を開く |
| 4×1 | 5つ目のタイルに **6本の縦ミニバー**(R N M A V S、アプリ一覧と同じ)+ 着信モードのアイコン | アプリを開く |
| 4×2 | 上段に **横バー + 数値** を 2列×3行(編集画面の並び)+ 着信モードと「変更あり」の文言 | アプリを開く |

- 入口は文字で示す(issue #10。アイコンだけでは伝わらなかった): 4×2 は見出しに「現在の音量」と「アプリを開く ›」、
  4×1 はタイルの下に「音量詳細」。ずれているときは「変更あり」、アクセス許可が無いときは「要許可」に変わる(色付き)
- 「アプリを開く」は**アプリアイコンと同じ動作**(前回の画面に戻る。初回は一覧)。`MainActivity` は `singleTask` で、
  ランチャーと同じ Intent(`MainActivity.launcherIntent`)で開く。以前は `CLEAR_TOP` で必ず一覧から作り直していたため、
  編集の途中でホームに戻ってウィジェットから開くと、未保存の内容が黙って消えていた(issue #12)
- 編集中の内容と開いている画面は `rememberSaveable` で保持するので、回転・テーマ切替・プロセスの再生成でも消えない
- 1×1 の循環は途中のプロファイルも実際に適用する(サイレントを通ると DND が一瞬オンになる)。目的のプロファイルに直接行けるよう、
  広げたときの「選ぶ」は `ProfilePickerActivity` を開く。幅 150dp 以上で `RemoteViews(Map<SizeF, …>)` が横長版を選ぶ
- バーは `ClipDrawable` を `src` にした `ImageView` に `setImageLevel(0..10000)` で長さを、
  `setColorFilter` で色(適用中プロファイルの色)を指定している。`ProgressBar` の色付けより素直
- 4×2 は `RemoteViews(Map<SizeF, RemoteViews>)` で高さ 180dp 未満なら音量表示を外した版を出す
  (縮めたときにプロファイルボタンが潰れないように。Pixel 9a の 4×2 は約 200dp)
- 押したときはセル・パネルに波紋を出す(`android:foreground="@drawable/widget_press"`、角丸に合わせたマスク付き ripple。issue #30)
- 4×1 は幅 250dp 未満になると音量タイルを外す(`RemoteViews(Map<SizeF, …>)`)。4×1 / 4×2 の最小幅は 180dp(4 列でも押せる大きさ)
- ウィジェット選択画面での名前は「1×1 タップで切替」「4×1 選んで切替」「4×2 音量も確認」
- `RemoteViews` で使えないビューがあるので(`Space` 等)、レイアウトは
  `FrameLayout` / `LinearLayout` / `ImageView` / `TextView` だけで組んでいる

### 5.6 アプリ外での音量変更への追従

ウィジェットのプロセスは普段死んでいるので、動的レシーバでは音量キーを拾えない。
`VOLUME_CHANGED_ACTION` はマニフェスト登録の暗黙ブロードキャスト例外にも入っていない。
フォアグラウンドサービスは常駐通知が出るので避けたい。

そこで **content-trigger の JobScheduler ジョブ**(`VolumeWatchJob`)を使う。
AudioService は音量を `Settings.System`(`volume_music_speaker` 等、出力デバイスごと)に、
着信モードを `Settings.Global.mode_ringer` に書き込むので、その URI の変化でジョブを起こせる。

- トリガー: `Settings.System.CONTENT_URI`(子孫も)+ `Settings.Global.MODE_RINGER`。
  update delay 100ms / max delay 1s(音量キー長押しは1回の再描画にまとまる)
- content-trigger ジョブは1回発火で終わる。`WidgetRefresher.refreshAll()` の最後に毎回スケジュールし直す。
  実行中に同じIDでスケジュールし直すと、その間に来た変更は次のジョブに引き継がれる
- 永続化(`setPersisted`)できないので、再起動後はランチャーが送る `APPWIDGET_UPDATE` → `onUpdate()` で張り直す
- ウィジェットが1つも無くなったら(`onDisabled` → `refreshAll`)キャンセル
- 実機ではプロセスを `am kill` した状態から、音量変更 → 約1秒でプロセス起動・再描画を確認(8.6)。
  無関係な System 設定の変化で頻繁に起きることもなかった

### 5.7 「変更あり」の判定

`VolumeApplier.matches(profile, device)`。適用直後に「変更あり」にならないよう、Android が勝手に書き換える値は比較しない。

- 着信モードは一致が必要(サイレント適用後も `getRingerMode()` はサイレントを返す。8.3 の内部モードとは別)
- `SYSTEM` は `RING` のエイリアス(8章)なので比較しない
- バイブ/サイレントでは `RING` / `NOTIFICATION` はミュートされ 0 を返すので比較しない
- 期待値は `StreamRanges` で丸めた値(issue #14)。範囲は `getStreamMinVolume()`〜`getStreamMaxVolume()`
  (通話・アラームは最小 1)で、**「着信音あり」の着信音は最小 1**(0 にすると Android がバイブに切り替えるため)。
  同じ範囲を編集画面のスライダー・±ボタン、保存、適用、比較のすべてで使うので、
  「画面では 0 なのに実際は 1 で鳴る」「着信音ありなのに適用直後から変更あり」が起きない
- ヘッドホン等で出力先が変わるとメディアの値も変わるので「変更あり」になる。実際に音量が違うので正しい挙動とする

### 5.8 適用に失敗したとき(issue #5)

`VolumeApplier.apply()` は `ApplyResult`(`Applied` / `NeedsAccess` / `Partial`)を返し、
**`Applied` のときだけ `applyActive()` する**。以前は各操作を `runCatching` で握りつぶしていたため、
「サイレント モードへのアクセス」が取り消されていると、着信モードの変更が失敗したまま一部の音量だけ書かれ、
それでも「適用中」と記録されていた。

- アクセスが無いときは何も変更しない(中途半端に一部だけ適用しない)
- アプリ内は Snackbar で結果を表示(「『通常』を適用しました」/ 失敗理由)
- ウィジェットは結果を出す手段がない。**トーストは使えない**: 通知権限のないアプリがバックグラウンドから出すトーストは
  Android が抑止する(`Suppressing toast from package ... by user request`、実機で確認)。
  代わりに再描画で状態を示す: アクセスが無い間は全セルのタップを「アプリを開く」に切り替え、
  4×2 は「許可が必要です(タップして開く)」、4×1 は音量タイルに警告アイコン、1×1 は「許可が必要」と表示する。
  アプリで許可して戻ると(`onPause` の再描画で)通常表示に戻る

### 5.9 クイック設定タイル(issue #11)

通知シェードからどの画面でも切り替えられるよう、`TileService`(`ProfileTileService`)を追加した。

- 表示: ラベルに適用中のプロファイル名、サブタイトルに「Volace」/「変更あり」/「許可が必要」/「タップして選ぶ」。
  アイコンは適用中プロファイルのアイコン。適用中かつ一致していればオン、それ以外はオフ。
  状態はパネルを開いたとき(`onStartListening`)に読み直す。Pixel の小さいタイルはアイコンだけなので、名前は大きいタイルで見える
- タップ: 透明な `ProfilePickerActivity` を `startActivityAndCollapse` で開き、プロファイル名の一覧から選ぶ
  (循環順を覚えなくてよい)。**適用はこの表示中の Activity から行う**: タイルのクリックが Android 17 の音量制限で
  ユーザー操作扱いになるかは文書に無いため(8.7)。API 34 以降は `PendingIntent` 版を使う(`Intent` 版は例外になる)
- 追加: アプリの「ウィジェット追加」ダイアログに「クイック設定タイル」を置き、`StatusBarManager.requestAddTileService()`
  でシステムの確認ダイアログを出す。ロック中は `unlockAndRun` で解除してから開く

### 5.10 ランチャーのショートカット(issue #22)

アプリアイコンの長押しメニューに、先頭4件のプロファイルを「『○○』を適用」として出す(`ProfileShortcuts`)。
メニューの ＋ やドラッグでホーム画面に固定もできる。

- アイコンはプロファイル色の上に白いプロファイルアイコンを描いた adaptive bitmap
- 起動先は透明な `ApplyShortcutActivity`(適用して閉じる)。ショートカットは Activity しか起動できず、
  Android 17 では表示中の Activity から適用するのが確実なため(8.7)
- 更新は `WidgetRefresher.refreshAll()` の中で行うが、**表示内容(id・名前・アイコン・色・順番)が変わったときだけ** `setDynamicShortcuts` する。
  ShortcutManager はバックグラウンドからの更新回数を制限しており、再描画は音量が変わるたびにバックグラウンドで走るため。
  制限で失敗したときは記録を更新せず、次の機会に再試行する
- 削除したプロファイルの固定ショートカットは無効化(「削除されたプロファイルです」)、名前などを変えたものは更新する

### 5.11 バックアップ(issue #23)

一覧の「バックアップ」から、全プロファイルを JSON ファイルに保存・復元する(`ProfileBackup`)。
ファイルはシステムの選択画面(Storage Access Framework)で選ぶので、ストレージ権限は要らない。

- 書き出すのはプロファイルを定義する値だけ(名前・着信モード・6音量・色・アイコン)。DB の id、適用中フラグ、
  orderIndex は書かない(並び順はファイル内の順番)。`format` / `version` で自分のファイルか判定する
- 読み込みは全項目を検査し、壊れたファイル・別アプリのファイルは理由付きで拒否する(途中まで取り込まない)
- 読み込み時は「追加する」(末尾へ)か「置き換える」(全件入れ替え、1 トランザクション)を選ぶ。
  値はこの端末の範囲に合わせ(`StreamRanges`)、読み込んだだけでは適用しない
- OS のバックアップ(クラウド・端末間転送)は `data_extraction_rules.xml` で範囲を明示: プロファイルの DB は含めるが、
  端末ごとの状態(`volace_device.xml`: ランチャーのショートカットの更新記録)は除外する。
  復元した記録が残っていると、新しい端末でショートカットを登録しなくなるため

### 5.12 多言語対応(issue #19)

画面・ウィジェット・タイル・ショートカット・メッセージの文言はすべて `strings.xml` にある。
**日本語が既定(`values/`)、英語が `values-en/`**。Android 13 のアプリ別の言語設定(設定 › アプリ › Volace › 言語)にも対応
(`locales_config.xml`)。追加・変更するときは両方のファイルを揃える。

- `VolumeStream.label` / `ProfileIcon.label` / `ringerModeLabel()` は文字列リソース ID を返す
- 既定プロファイルの名前は、作られたときの言語で保存される(あとから言語を変えても名前は変わらない)
- バックアップのエラーは `FormatException(reason = R.string.…, args)` で持ち、表示するときに訳す
- **ウィジェットの文言はすべて Volace 側で設定する**: レイアウトに書いた `@string` はホーム画面アプリが
  *端末全体の言語* で解決するため、Volace だけ別の言語にすると日英が混ざる。見出しやボタンにも id を付けて `setTextViewText` する
  (レイアウトの `@string` はウィジェット選択画面のプレビュー用)。音量名の欄の幅も言語で変わるので `setViewLayoutWidth` で設定する
- 英語の音量名(Notifications など)は長いので、ラベル幅を `dimens.xml` で言語ごとに持つ

### 5.13 スキン(issue #20)

一覧の「設定」→「スキン」で、アプリとウィジェットの見た目を選ぶ(`Skin` / `SkinStore`、設定は SharedPreferences)。

| スキン | 明暗 | 内容 |
|---|---|---|
| 既定(ダーク) | 暗 | これまでの見た目 |
| ライト | 明 | 明るい背景 |
| 端末に合わせる | 端末に追従 | ライト / 既定を切り替え |
| Material You | 端末に追従 | 壁紙の色(`system_accent*` / `system_neutral*`)、アプリは `dynamic*ColorScheme` |
| ハイコントラスト | 暗 | 黒と白、黄色のアクセント |
| ミッドナイト | 暗 | 深い紺 |

- ウィジェットの色は `WidgetPalette`(明・暗の2組)で持ち、`RemoteViews.setColorInt` / `setColorStateList(… night)` で渡す。
  **明暗はホーム画面アプリが自分の設定で選ぶ**ので、「端末に合わせる」「Material You」はダークテーマを切り替えると再描画なしで追従する。
  背景は `setBackgroundTintList` で塗るので角丸の形は保たれる。Material You の色は描画時に読むので、壁紙を変えたら次の再描画で反映
- プロファイル色を明るい背景に置くときは濃く、暗い背景では明るくして見分けやすくする(`tintFor`)
- **プロファイル色の上の文字・アイコン**(適用中のセル、アプリのアイコン、「適用中」バッジ、ショートカット)は `contentColorOn` で選ぶ:
  ほとんどの色は白のまま、明るい色(黄色 #FFB300 など、輝度 0.5 超)だけ濃い色にする。白だと約 1.8:1 で読めなかった
- ウィジェットの角丸は端末の値(`system_app_widget_background_radius` / `inner_radius`)に合わせ、`clipToOutline` で中身も切る
- スキンによってはアプリが暗いのに端末が明るい(逆も)ので、ステータスバー・ナビゲーションバーのアイコン色もスキンに合わせる

### 5.14 時間指定(issue #24)

「1 時間だけサイレント」のように、プロファイルを決めた時刻まで適用し、終わったら戻す(`timer/`)。

- **入口**: 一覧の各行の「時間指定」と、選択画面(QS タイル・1×1 の「選ぶ」)の ⏱。
  ダイアログで「30 分 / 1〜3 時間 / 時刻を指定」(デバッグ版のみ「1 分」も)と、終わったら「適用前の状態に戻す」か「別のプロファイルにする」を選ぶ
- **記録**: タイマーは 1 つだけ。`volace_device.xml` の `timer` に JSON で保存(バックアップ対象外)。
  開始直前の着信モード・音量(`DeviceVolumes`)と、その時「適用中」だったプロファイルも持つ。
  タイマー中に別のタイマーを始めても、戻り先は最初のタイマーの前の状態のまま
- **終了**: `AlarmManager.setExactAndAllowWhileIdle`(`USE_EXACT_ALARM`、インストール時に許可済み)で `TimerReceiver` を起こし、
  `TimerService` に終了を頼んで、**FGS の中で**戻す(サービスが止まっていれば、正確なアラームから開始し直す)
- **Android 17 の制限への対応(8.7)**: バックグラウンドから音量を変えられるのは FGS が動いている間だけ(targetSdk 36 のため、
  「ユーザー操作から始めた FGS」でなくてよい)。開始時に表示中の画面(一覧・選択画面)から `TimerService`(FGS、`specialUse`)を開始し、
  終了まで「今すぐ戻す」「30 分延長」付きの通知(カウントダウン表示)として動かす
- **戻せなかったとき**: Android は無視したことを知らせないので、戻した後に読み直して確かめる(`matches`)。
  違っていればタイマーを「時間切れ」のまま残し、「タップすると戻します」の通知を出す。通知のタップは透明な `TimerActionActivity`(表示中の Activity)で戻す
- **再起動・アプリ更新の後**(`TimerBootReceiver`): アラームを登録し直し、`TimerService` を開始し直す。電源が切れている間に終了時刻が
  過ぎていたら、すぐ鳴るアラームを登録する(レシーバーからは音量を変えられないため、アラーム → FGS の経路に乗せる)
- **スケジュールとの関係(5.15)**: 時間指定の途中でスケジュールの時刻が来たら、時間指定はそのまま続け、終わったときの戻り先をスケジュールのプロファイルに替える
- **取り消し**: 手動で別のプロファイルを適用(一覧・ウィジェット・タイル・ショートカット)したら、タイマーは消して音量はそのまま。
  編集の「保存して適用」は同じプロファイルの再適用なので消さない
- **戻す内容**: 「適用前の状態」はプロファイルと同じ経路(`VolumeApplier.apply`)で書く。バイブ・サイレント中は着信音・通知・システムが
  ミュートで 0 と読めるため、その 3 つは「変更しない」にして、着信音ありに戻ったときの音量を壊さない
- **表示**: 現在の音量カードに「15:00 まで「マナー」・終わったら「適用前の状態」」と「30 分延長」「今すぐ戻す」。
  ウィジェット(4×2 の状態行・4×1 の状態タイル・1×1 の説明)と QS タイルの副題に「15:00 まで」
- **通知の許可**: 最初の時間指定のときに `POST_NOTIFICATIONS` を求める。断ってもタイマーは動く(通知が出ないだけ)

### 5.15 スケジュール(issue #26)

「平日の 22:00 に『マナー』、7:00 に『通常』」のように、決めた時刻にプロファイルを切り替える(`schedule/`)。

- **入口**: 一覧の「現在の音量」の下の「スケジュール」カード(次の切り替えと、前回の失敗を表示)→ スケジュール画面
- **ルール**: 「時刻・曜日 → プロファイル」の組(`ScheduleRule`、表 `schedule_rules`)。オン・オフを切り替えられる。
  同じ曜日・時刻に複数あるときは新しく作ったほう(id が大きいほう)を使う(編集ダイアログで注意を出す)
- **休む日**: 祝日・休暇など、切り替えない日を期間で手入力する(`ScheduleSkip`、表 `schedule_skips`)。祝日の API は使わない。
  過ぎた期間はスケジュール画面を開いたときに消す
- **動き方**: 時刻(境界)ごとに **1 回だけ** 切り替える。手動で切り替えたら、次の境界まではそのまま(上書きしない)。
  次の境界は `ScheduleCalc.next`、鳴ったときに適用する境界は `ScheduleCalc.latestDue`(前回処理した時刻より後で、今以前の最新のもの)。
  どこまで処理したかは `volace_device.xml` の `schedule_handled_until` に持つ。ルールを編集したとき・**手動でプロファイルを選んだとき**
  (一覧・ウィジェット・タイル・ショートカット・時間指定・失敗の通知のタップ。`Schedules.noteManualChoice`)は「今」にし、それより前の境界は適用しない。
  手動で選んだら、待っている失敗の通知も消し、結果を「手動で切り替えた」/「手動の選択を優先した」にする
- **Android 17 の制限への対応(8.7)**: 正確なアラーム(`ScheduleReceiver`)から `ScheduleService`(FGS、`specialUse`)を開始し、
  その中で切り替えてすぐ止める。正確なアラームはバックグラウンドから FGS を開始できる例外に当たり、targetSdk 36 なら
  その FGS(ユーザー操作なし)でも音量を変えられる。FGS の通知は 1 秒ほどで消えるので、Android は普通は表示しない
- **失敗したとき(A)**: 切り替えた後に読み直し(`matches`)、違っていれば「『○○』に切り替えられませんでした。タップすると切り替えます」の
  通知を出す(タップは透明な `ScheduleActionActivity` で適用)。おやすみモードの権限が無いとき・プロファイルが削除されていたときは、アプリを開く通知。
  結果(`ScheduleStatus`: 切り替えた / 時間指定の後に / 失敗 / 権限なし / プロファイルなし / 失敗の後に手動で切り替えた / 手動の選択を優先した)は
  `schedule_status` に保存し、一覧のカードとスケジュール画面に出す。**失敗を成功扱いしない**
- **時間指定との関係**: 時間指定の途中で境界が来たら、時間指定は手動の選択なので最後まで続け、終わったら境界のプロファイルにする(`ProfileTimers.handOverLocked`)。
  時間切れで戻せずに待っている時間指定は、スケジュールの切り替えで取り消す
- **アラームの登録し直し**(`ScheduleBootReceiver`): 再起動・アプリ更新・時刻の変更・タイムゾーンの変更と、アプリを開いたとき(強制停止で消えるため)。
  電源が切れている間に境界を過ぎていたら(7 日前まで)、すぐ鳴るアラームを登録して最新の境界を適用する。時計が戻されたら、処理済みの時刻も今に戻す
- **プロファイルの削除**: そのプロファイルに切り替えるルールも削除する(確認ダイアログに件数を出す)。
  バックアップを「置き換える」で読み込むと全プロファイルの id が変わるため、ルールは「削除されたプロファイル」と表示され、時刻が来たら失敗の通知になる。
  外部キー(カスケード削除)にしなかったのは、この置き換えでスケジュールが黙って全部消えるのを避けるため
- **バックアップ**: JSON のバックアップ(5.11)にはまだ含めない。OS のバックアップには DB ごと含まれる
- **通知の許可**: 最初にルールを保存したときに `POST_NOTIFICATIONS` を求める(失敗の通知に要る)

### 5.16 プロファイルごとの音(issue #28)

プロファイルに端末の標準の着信音・通知音・アラーム音を持たせ、適用時に切り替える(`audio/ProfileSounds.kt`)。

- **値**: 種類ごとに null(変更しない、既定)/ ""(なし)/ 音の URI。編集画面の「音」で、行をタップすると
  システムの音の選択画面(`RingtoneManager.ACTION_RINGTONE_PICKER`。Pixel では Google の SoundPicker)が開く。
  「既定」は出さない(プロファイルが書き換える設定そのものを指すため)。× で「変更しない」に戻す
- **適用**: `VolumeApplier.apply` の最後に `RingtoneManager.setActualDefaultRingtoneUri`。音の変更は「変更あり」の判定(`matches`)に含めない
- **許可**: 「システム設定の変更」(`WRITE_SETTINGS`、特別なアクセス)が要る。マニフェストに宣言しないと設定画面のスイッチが押せない。
  音を選んでいて許可が無いと、編集画面に案内(「許可する」で設定画面を開き、戻ったら読み直す)。
  許可が無いまま適用すると、音以外は書いて「一部(音)を変更できませんでした」(`Partial`)にする。黙って成功扱いしない
- **効く範囲**: 標準の音だけ。ほかのアプリが独自に設定した通知音(通知チャンネルの音)や、時計アプリのアラームごとの音は変わらない(画面で説明)
- **時間指定(5.14)**: 時間指定のプロファイルが変える種類だけ、開始前の音を `ProfileTimer.previousSounds` に持ち、「適用前の状態に戻す」で書き戻す
- **バックアップ(5.11)**: 音の URI は端末ごとに違う(`content://media/internal/audio/media/<番号>`)ので JSON には含めない。読み込んだプロファイルは「変更しない」になる

### 5.17 おやすみモード(issue #27)

プロファイルごとに「おやすみモード」を選び、今の状態(何が鳴るか)を表示する(`audio/DndModes.kt`)。

- **選択肢**(編集画面の「おやすみモード」): 使わない(既定。Volace のモードをオフ)/ 重要な通知のみ / アラームのみ
- **Volace のモード**: `AutomaticZenRule` を 2 つ登録する(「Volace(重要な通知のみ)」「Volace(アラームのみ)」。システムの「モード」一覧に出る)。
  1 つのルールのフィルタを書き換えないのは、ユーザーがモードの設定画面で変えた項目を Android がアプリの更新より優先するため。
  どちらも「重要な通知のみ」の種類(`INTERRUPTION_FILTER_PRIORITY`)で、「アラームのみ」は `ZenPolicy` でアラームとメディアだけ許可する。
  Android の「アラームのみ」フィルタは着信モードを内部でサイレントにし、ほかのモードと重なると終了後に戻らないことがあったため(8.9)。
  ルール ID は `volace_device.xml` に持ち、ユーザーがモードを削除していたら作り直す。どのプロファイルも使わなくなったモードは消す
  (アプリ起動時と編集画面から戻ったとき。`removeUnused`)
- **適用の順序**(`VolumeApplier.apply`): ① Volace のモードをオフ ② 着信モードと音量 ③ プロファイルのモードをオン。
  オフにしてからオンにするので、ユーザーがシステムの「モード」から一時停止していても切り替わる。
  `Condition` の source は、手動の切り替えが `SOURCE_USER_ACTION`、スケジュール・時間指定の終了が `SOURCE_SCHEDULE`
- **ほかのモードを止めない**: アプリが着信モードを「着信音」「バイブ」にすると、Android はおやすみモードをオフにし、
  そのとき**ほかのモード(おやすみ時間・車内・運転中・手動のおやすみモード)もまとめて一時停止する**(8.9)。そこで ① の後もおやすみモードが
  オンなら、それが Volace の「サイレント」が入れたもの(`dnd_silent_by_volace`)でない限り、着信モードと、着信モードに連動する音量
  (着信音・システム。書くと着信モードが変わりうる)は書かない。ほかの音量は書く。ほかのモードが終われば Android が着信モードを戻す
- **「サイレント」との関係**: 着信モードを「サイレント」にすると Android がおやすみモード(重要な通知のみ)を自動でオンにする(8.3)。
  それまでおやすみモードがオフだったら Volace が入れたものとして覚え、次に「着信音」「バイブ」のプロファイルを適用したときに(これまでどおり)解除する
- **「変更あり」の判定**: Volace のモードがプロファイルどおりかを比べる。おやすみモード中は着信モードがサイレントと読め、着信音・通知も
  ミュートで 0 と読めるので、それらはおやすみモードがオフのときだけ比べる
- **表示**: 現在の音量カードに「おやすみモード: 重要な通知のみ・アラームは鳴る」(ほかのモードなら「(ほかのモード)」)。
  アラームが鳴るかは `consolidatedNotificationPolicy` で判断する。4×2 ウィジェットの状態行に「おやすみ(アラーム可)」、4×1 に「おやすみ」。
  カードは `ACTION_INTERRUPTION_FILTER_CHANGED` で、ウィジェットは `zen_mode` の content-trigger(`VolumeWatchJob`)で更新する
- **時間指定**: 開始前の Volace のモードも記録し、「適用前の状態に戻す」で戻す
- **バックアップ**: `"dnd": "off" | "priority" | "alarms"`(古いファイルは「使わない」)
- 権限は追加なし(`ACCESS_NOTIFICATION_POLICY` で足りる)

## 6. パーミッション

```xml
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
<uses-permission android:name="android.permission.ACCESS_NOTIFICATION_POLICY" />
```
- どちらも通常権限(ランタイムダイアログなし)
- ただし `ACCESS_NOTIFICATION_POLICY` は宣言だけでは不十分で、ユーザーが設定画面から個別に許可する必要がある(4.3のオンボーディングで対応)
- Accessibility Service・Device Admin は不要
- 外部での音量変更への追従(5.6)は JobScheduler の content-trigger で、追加の権限は要らない
- プロファイルごとの音(5.16)のために `WRITE_SETTINGS`(「システム設定の変更」)。特別なアクセスで、ユーザーが設定画面で許可する
  (`ProtectedPermissions` の lint は抑止。音を選んだプロファイルの編集画面から案内する)
- 時間指定(5.14)のために次を追加:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.USE_EXACT_ALARM" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```
  - FGS は時間指定の間と、スケジュールの切り替えの 1 秒ほどだけ動く(常駐はしない)。種類は該当するものが無いので `specialUse`
  - スケジュール(5.15)は追加の権限なし。時刻・タイムゾーンの変更(`TIME_SET` / `TIMEZONE_CHANGED`)はマニフェストで受け取れる例外
  - `USE_EXACT_ALARM` は Google Play では時計・カレンダー系のアプリに限られる。Play で配布するなら
    `SCHEDULE_EXACT_ALARM`(ユーザーが許可)に替える。許可が無ければ非正確なアラームになり、終了が数分遅れうる
  - `POST_NOTIFICATIONS` は実行時に許可を求める(初回の時間指定・スケジュールのルールを初めて保存したとき)

## 7. プロジェクト構成

```
volace/
  app/
    src/main/kotlin/com/hong/volace/
      data/
        Profile.kt              // Room Entity(色・アイコンを含む)
        ProfileAppearance.kt    // ProfilePalette(10色) / ProfileIcon(14種)
        ProfileDao.kt           // CRUD + applyActive() + move()
        VolaceDatabase.kt       // version 2, MIGRATION_1_2
        DefaultProfiles.kt      // 初回起動時に生成する4プロファイル
      audio/
        VolumeApplier.kt        // AudioManager 操作の一元化クラス(snapshot / matches も)
        DeviceVolumes.kt        // 端末の現在の音量・着信モード
        StreamInfo.kt           // 6ストリームの定義・アイコン・ラベルマッピング
      ui/
        list/ProfileListScreen.kt
        list/CurrentVolumeCard.kt  // 「現在の音量」カードと音量変化の購読
        edit/ProfileEditScreen.kt
        onboarding/OnboardingScreen.kt
        theme/Theme.kt
      widget/
        WidgetStyle.kt          // 3バリエーションの定義とセル・音量バーのview id表
        WidgetRenderer.kt       // profiles + 端末の音量 → RemoteViews
        WidgetPlumbing.kt       // provider×3 / VolumeApplyReceiver / WidgetRefresher
        VolumeWatchJob.kt       // アプリ外での音量変更でウィジェットを再描画
      MainActivity.kt
    src/main/res/
      drawable/ic_profile_*.xml        // プロファイル用アイコン14種(アプリ・ウィジェット共通)
      drawable/ic_launcher_*.xml       // アプリアイコン(前景/背景/モノクロ)
      drawable/widget_*.xml            // ウィジェットの角丸shape・音量バー(ClipDrawable)
      layout/widget_{1x1,1x4,2x4}.xml
      mipmap-anydpi-v26/ic_launcher*.xml
      xml/widget_info_{1x1,1x4,2x4}.xml
    build.gradle.kts
  build.gradle.kts
  settings.gradle.kts
  DESIGN.md   ← 本ファイル
```

### 7.1 アプリアイコン
ミキサーのフェーダー3本をモチーフにした adaptive icon。前景・背景ともベクタで、
`monochrome` レイヤーも用意しているので Android 13+ のテーマアイコンにも対応する。
背景は #1E3A8A → #2563EB → #06B6D4 のリニアグラデーション。

- パッケージ名(仮): `com.hong.volace`(サイドロードのみなのでグローバル一意性は重要でないが、将来公開する可能性を考え衝突しにくい名前にしておく)
- アプリ名(仮): **Volace**(プロジェクトフォルダ名を踏襲)

## 8. 実機検証の結果(Pixel 9 Pro XL / Android 17, SDK 37, build CP2A.260805.005)

2026-08-21、実機(USB接続・サイドロード)で検証済み。

- **`STREAM_SYSTEM` は `STREAM_RING` にエイリアスされている**(`dumpsys audio` で `STREAM_SYSTEM (aliased to: STREAM_RING)` を確認)。プログラムから独立に設定しても、最後に呼んだ方の値が両方に反映される
  - → `VolumeApplier.apply()` で SYSTEM を先に、RINGER を最後に適用する順序に修正済み(`APPLY_ORDER` 定数)。UI上は6項目とも独立スライダーとして残すが、実際にはRingerの値が優先される
- Notification は Ring と独立して読み書き可能(実機で確認、エイリアスなし)
- `STREAM_VOICE_CALL` は通話中でなくても `getStreamMaxVolume`/`getStreamVolume`/`setStreamVolume` が問題なく動作(15段階中の値を取得・維持)
- Media ストリームの段階数は 0-25(旧世代Android の 0-15 より細かい)
- DND(通知ポリティクスアクセス)オンボーディング → 設定画面誘導 → 許可 → `onResume()` での再検知、の一連のフローは正常動作
- Room への保存・一覧反映・タップでの即時適用まで、クラッシュなく動作確認済み
- 端末には旧 Volume Ace(`it.braincrash.volumeace`)の音量変更履歴が `dumpsys audio` のログに残っており、同一機種で長年使われていたことを確認(参考情報)

### 8.1 Glance をやめた経緯(2026-08-21)
ウィジェットのタップで音量適用とDB更新は正常に行われるのに、**ウィジェットの表示だけが更新されない**問題が発生。
実機ログで以下を確認したうえで、Glance を捨てる判断をした。

| 試したこと | 結果 |
|---|---|
| `VolaceWidget().updateAll(context)` | 例外なく完了するが `provideGlance` が再実行されない |
| `update(context, glanceId)` | 同上 |
| `GlanceAppWidgetManager.getGlanceIds()` | `[AppWidgetId(appWidgetId=18)]` を返す(ID自体は正しく登録されている) |
| `AppWidgetManager.updateAppWidget()` に素の RemoteViews | これでも視覚的な変化なし |
| `am broadcast APPWIDGET_UPDATE` | protected broadcast のためアプリからは送信不可(`SecurityException`) |

素の `AppWidgetManager` 呼び出しですら効かなかったことから、Glance のセッションが自前の RemoteViews を
保持し続けて上書きしているものと推定。5章の構成に全面的に書き換えた。

### 8.2 マイグレーション検証(2026-08-21)
- v1 の既存3件を保持したまま v2 へ移行し、`colorArgb` / `iconKey` が既定値で追加されることを確認
- DBを削除して再起動すると `DefaultProfiles` の4件が生成され、
  端末実測値(ring 0-7, media 0-25, alarm 0-7, voice 0-15 等)に沿った値が入ることを確認
- 3つの provider がすべて `dumpsys appwidget` に登録されることを確認

### 8.3 サイレント指定時の Android の挙動(2026-08-21 実機確認)

`AudioManager.ringerMode = RINGER_MODE_SILENT` を呼ぶと、この端末では:

| 項目 | 結果 |
|---|---|
| `global zen_mode` | 0 → **1**(DND: 重要な通知のみ)がオンになる |
| `global mode_ringer` | **1**(バイブ)。0(サイレント)にはならない |
| 「着信音」に戻したとき | `zen_mode` は自動で 0 に戻り、`mode_ringer` も 2 になる |

これは AOSP の `ZenModeHelper.RingerModeDelegate.onSetRingerModeExternal` の仕様どおりの動作で、
アプリ側から回避する手段はない(対称的に元へ戻るので実害はない)。
分かるように、編集画面で「サイレント」を選んでいるときだけ注記を表示するようにした。

また、着信音量を0にすると OS が勝手にバイブへ落とすため、
`VolumeApplier.apply()` は **モード → 全ストリーム音量 → モード(再指定)** の順で適用し、
プロファイルの指定を最後に確定させている。`RINGER_MODE_NORMAL` のときだけ、
モード再指定で戻される可能性のある Ring/Notification をもう一度書き直す。

### 8.4 新ウィジェットの実機検証(2026-08-21)

| 確認項目 | 結果 |
|---|---|
| 4×2 をホーム画面に配置 | OK(アプリ内の「ウィジェットを追加」経由) |
| セルのタップ → 音量適用 | OK(6ストリームすべて期待値どおり) |
| **タップ後のウィジェット再描画** | **OK**(ハイライトが即座に移動。Glance で解決できなかった問題は解消) |
| 4件のときに2段目が消える | OK(1段目のボタンが縦に広がる) |
| 1×1 のタップ循環 | OK(通常 → マナー → サイレント → 音楽 → 通常 と巡回) |
| **別ページの別ウィジェットへの同期** | OK(1×1 を押すと3ページ目の 4×2 も更新される) |
| 着信モード(NORMAL / VIBRATE) | OK。SILENT のみ 8.3 の挙動 |
| 一覧のミニバー | OK(各ストリームの最大値で正規化され、実値と一致) |
| アプリアイコン | OK(アダプティブアイコンとして正しく描画) |

### 8.5 Pixel 11 Pro での検証(2026-08-21)

`grizzly` / Android 17 / SDK 37 / build CD1A.260618.001.A7。**9 Pro XL と音声まわりの条件は完全に同一**だった。

| 項目 | Pixel 9 Pro XL | Pixel 11 Pro |
|---|---|---|
| 着信音 / 通知 / アラーム / システム の段階 | 0-7 | 0-7 |
| メディア | 0-25 | 0-25 |
| 通話 | 0-15 | 0-15 |
| `STREAM_SYSTEM` のエイリアス | → `STREAM_RING` | → `STREAM_RING`(同じ) |

- リリースAPKのインストール、`cmd notification allow_dnd` による DND アクセス付与、
  初回起動時の既定プロファイル4件の生成まで確認。クラッシュなし
- ライトテーマ端末での表示も確認(9 Pro XL はダークテーマ)

### 8.6 現在の音量表示の実機検証(Pixel 9a / Android 17, SDK 37, 2026-10-04)

| 確認項目 | 結果 |
|---|---|
| 4プロファイルそれぞれ適用直後に「適用中」(誤って「変更あり」にならない) | OK |
| アプリ表示中に外部から音量変更 → カードと「変更あり」がその場で更新 | OK |
| 4×2 / 4×1 の音量表示(バー・数値・着信モード) | OK |
| **プロセスを kill した状態で外部から音量変更 → ウィジェット更新** | OK(約1秒) |
| 外部から着信モードをバイブに → ウィジェットのアイコン・バー更新 | OK |
| 「変更あり」のセルをタップ → 再適用され塗りに戻る | OK |
| 音量表示のタップ → プロファイル一覧が開く(編集画面のまま裏にいても) | OK |

- adb の `input keyevent KEYCODE_VOLUME_*` と `cmd media_session volume --set` は、この端末では
  **AudioHardening により無視される**(`dumpsys audio` に "volume control ... would be ignored")。
  外部からの音量変更の再現には `adb shell cmd audio set-volume <stream> <index>` /
  `cmd audio set-ringer-mode VIBRATE` を使う

### 8.7 Android 17 のバックグラウンド音量制限(AudioHardening)の検証(2026-10-04, issue #7)

Android 17 では、`setStreamVolume()` / `setRingerMode()` などを呼べるのは
「表示中の Activity」か「`SHORT_SERVICE` 以外のフォアグラウンドサービス」からに限られ、
targetSdk 37 ではさらにそのサービスに while-in-use(WIU)能力が要る。条件を満たさない呼び出しは**例外も出ずに無視される**。
ただし「ウィジェット操作・通知のクリック」はユーザー操作として扱われる。
https://developer.android.com/about/versions/17/changes/bg-audio

この端末(Pixel 9a / Android 17)の既定は「記録のみ」に近い状態だったので、強制した状態で確かめた。

```sh
adb shell cmd audio set-hardening enable   # 強制(mHardeningOverride=2)
adb shell cmd audio set-hardening throw    # 違反時に例外(=3)
adb shell cmd audio clear-hardening        # 既定に戻す(=0)
adb shell dumpsys audio | grep -E "mHardeningOverride|AudioHardening"
```

| 経路 | enable | throw |
|---|---|---|
| ウィジェットのセルをタップ(`BroadcastReceiver` + goAsync) | OK(全ストリーム・着信モードとも反映) | OK(例外なし) |
| アプリの一覧をタップ(表示中の Activity) | OK | OK |

- どちらの経路でも `dumpsys audio` の "Hardening enforcement" に Volace の記録は出なかった(違反扱いされていない)。
  記録が出たのは adb の `cmd media_session volume`(`com.android.server.media`)だけ
- **今のウィジェット・アプリ・(予定の)ショートカット/QS タイルからの手動適用は、強制後もそのまま動く**
- **ユーザー操作を起点にしない自動適用**(時刻の `AlarmManager`、Bluetooth 接続のブロードキャスト、ジョブ)は
  この制限に当たる見込み。実装する機能ごとに次のどれかで対応し、上の `set-hardening` で確かめること
  - ユーザー操作の時点で FGS を張っておく(例: 時限適用の開始時)
  - 予定時刻に通知を出し、タップで適用する(通知のクリックはユーザー操作扱い)
  - targetSdk を 36 にとどめる(WIU 要件が無くなり「`SHORT_SERVICE` 以外の FGS」だけで足りる。サイドロードなので可能)
    → **#26 でこれを採用した**(下の「OS の判定と targetSdk 36」)
- QS タイルのクリックがユーザー操作扱いかは文書に無いので、タイルからは表示中の Activity を経由して適用する

**時間指定(#24、5.14)の確認(2026-10-04、`set-hardening enable` で強制した状態)**

| 手順 | 結果 |
|---|---|
| 一覧で「通常」を 1 分の時間指定で適用(サイレント中から) | 適用され、FGS が開始(`Background started FGS: Allowed … uidState: TOP … allowWiu:12`)、正確なアラーム(`exactAllowReason=policy_permission`)が登録された |
| ホームに戻り、画面を消して終了時刻を待つ | サイレント・メディア 8・アラーム 4・通話 9 に戻り、FGS も止まった。Hardening enforcement に Volace の記録なし |

続き(2026-10-04、ロック解除後。この時点で targetSdk 36、#26 の変更を含む。強制設定は既定):

| 手順 | 結果 |
|---|---|
| QS タイル → 選択画面の ⏱ から「マナー」を 30 分 | 適用され、タイルの副題と 4×1・1×1・4×2 のウィジェットに「15:52 まで」が出た |
| 通知の「30 分延長」 | 終了が 16:22 になり、アラームも付け替わった |
| 通知の「今すぐ戻す」 | 「サイレント」に戻り、タイマー・通知・アラームが消えた |
| 終了時刻(開始時の FGS が動いている) | アラーム → `TimerService` の中で、元の音量(メディア 8・アラーム 4・通話 9)に戻った |
| 途中でアプリを入れ直して FGS を止め、終了時刻を待つ | `PACKAGE_REPLACED` から能力なし(`allowWiu:-1`)で開始し直した `TimerService` の中で戻せた |

未確認: 実際の再起動(アプリの入れ直しで代用した)。

**OS の判定と targetSdk 36(2026-10-04、issue #26)**

Android 17 のソース(`android17-release` の `services/core/java/com/android/server/audio/HardeningEnforcer.java`
の `blockVolumeMethod`)では、音量・着信モードの変更を次のように判定している。

1. 2 段階の制限を AppOps で調べる。**partial**(`OP_CONTROL_AUDIO_PARTIAL`: 表示中の Activity か FGS が要る)と、
   **full**(`OP_CONTROL_AUDIO`: さらにユーザー操作から始めた FGS が要る)。両方満たせば許可
2. 満たしていなければ、どちらの段階で止めるかを決める
   - `set-hardening enable` / `throw` のとき: **アプリに関係なく full**(targetSdk・例外を見ない)
   - 既定のとき: 特権アプリは許可。**正確なアラームの権限(`USE_EXACT_ALARM` / `SCHEDULE_EXACT_ALARM`)があれば partial**。
     それ以外は、機能フラグ(`hardeningPartialVolume`)が無効なら許可(記録だけ)、targetSdk 37 未満なら partial、37 なら full
3. 記録の `level` は 1 の結果(`partial` = FGS も無い、`full` = FGS はあるがユーザー操作からではない)、
   `would be ignored` は記録だけで実際は変更されたことを表す

つまり **`set-hardening enable` での確認は、targetSdk 36 や正確なアラームの例外を無視した、最も厳しい条件での確認**になる。
実際の動きを確かめるには既定(`clear-hardening`)で試す。Volace は `USE_EXACT_ALARM` を持つ(#24 から)ので、既定でもすでに partial が効いている。

試験(Pixel 9a、画面ロック中、正確なアラームから)で、メディアの音量を 8 → 7 に変えられるかを確かめた:

| 方法 | targetSdk | 強制設定 | 結果 |
|---|---|---|---|
| アラームのレシーバーから直接 | 36 | 既定 | 無視(`level: partial`) |
| アラーム → FGS(`specialUse`、ユーザー操作なし) | 37 | `enable` | 無視(`level: full`) |
| 同上 | 36 | `enable` | 無視(`level: full`。強制は targetSdk を見ない) |
| 同上 | 36 | 既定 | **変更できた**(FGS 開始の 0〜3 秒後とも) |
| 同上 | 37 | 既定 | 変更できた(正確なアラームの例外による) |

- 正確なアラームの例外は、ソースでは音量全般に効くが、公式文書では「アラームの音量だけ」とされている。将来ソースが文書に合わせられると
  効かなくなるので、文書で保証されている **targetSdk 36** を採用した(どちらでも partial になる)
- 将来 targetSdk 36 にも full が求められた場合は、読み直しで失敗を検出し、通知のタップ(ユーザー操作)で切り替える経路に落ちる

**スケジュール(#26、5.15)の確認(2026-10-04、画面ロック中、DB にルールを直接入れて確認)**

| 手順 | 結果 |
|---|---|
| 既定の状態で 14:39「マナー」、14:40「サイレント」 | どちらも正確なアラームで FGS が開始され(`Allowed … code:ALARM_MANAGER_WHILE_IDLE … allowWiu:-1 … targetSdkVersion:36`)、切り替わった。結果は「切り替えた」、次のアラームは翌日 |
| `set-hardening enable` で 14:42「マナー」 | 切り替えは無視され、読み直しで検出して「失敗」と記録、「タップすると切り替えます」の通知(`schedule_failed`)が出た |

続き(2026-10-04、ロック解除後):

| 手順 | 結果 |
|---|---|
| スケジュール画面でのルール・休む日の作成・編集・削除 | 問題なし(ユーザーも確認) |
| `set-hardening enable` で失敗させ、通知をタップ | 強制状態でも透明な Activity から「マナー」に切り替わり、記録は「手動で切り替えた」、通知は消えた |
| 時間指定(「サイレント」15:17 まで・終わったら適用前の状態=「マナー」)の途中、15:15 に「サイレント」の境界 | 時間指定は続き、戻り先が「サイレント」に付け替わった(記録は「時間指定が終わったら」)。15:17 に「サイレント」になった |
| アプリを開いている間の境界 | 切り替わった |

### 8.8 プロファイルごとの音の確認(Pixel 9a / Android 17、2026-10-04、issue #28)

| 手順 | 結果 |
|---|---|
| 編集画面で着信音・通知音を選ぶ | Google の SoundPicker が開き、選んだ音の名前(コピーキャット・デュエット)が出た。許可が無いので案内が出た |
| 許可が無いまま適用 | 「一部(音(「システム設定の変更」の許可が必要))を変更できませんでした」。音は変わらなかった |
| 「許可する」→ スイッチをオン → 戻る | 案内が消えた(最初はマニフェストに `WRITE_SETTINGS` が無く、スイッチが灰色で押せなかった) |
| 適用 | 着信音 Copycat・通知音 Duet に変わり、アラーム音(変更しない)はそのまま |
| 1 分の時間指定 → 終了 | 開始前の音(Your New Adventure・Eureka)に戻った |

### 8.9 おやすみモードとほかのモード(Pixel 9a / Android 17、2026-10-04、issue #27)

Android 17 の `ZenModeHelper.RingerModeDelegate.onSetRingerModeExternal` は、アプリが着信モードを「着信音」「バイブ」にしたとき、
おやすみモードがオンなら `setManualZenMode(OFF, ORIGIN_SYSTEM)` を呼ぶ。その中で、ユーザー操作でない(`ORIGIN_USER_IN_SYSTEMUI` でない)ときは
**有効な自動ルールをすべて `OVERRIDE_DEACTIVATE`(一時停止)にする**。

| 手順 | 結果 |
|---|---|
| 変更前の Volace で、「車内」モードをオンにしてから「マナー」を再適用 | 「車内」が `OVERRIDE_DEACTIVATE` になり、おやすみモードがオフになった(ほかのモードを止めていた) |
| 変更後、「車内」オンのまま「マナー」「通常」を適用 | 「車内」はオンのまま。着信モードは変わらず、メディアなどの音量だけ変わった。カードは「(ほかのモード)」、マナーは「適用中」 |
| 「車内」オンのまま「アラームのみ」のプロファイルを適用 | 「Volace(アラームのみ)」が登録されてオン、「車内」もオン |
| 続けて「使わない」のプロファイル | Volace のモードだけオフ。「車内」はオンのまま |
| 手動のおやすみモード(`cmd notification set_dnd priority`)中に「通常」 | おやすみモードはオンのまま |
| 「サイレント」→「マナー」 | 「サイレント」で入ったおやすみモードが解除された(これまでどおり) |
| 「アラームのみ」のプロファイルを削除 | 「Volace(アラームのみ)」がシステムのモード一覧から消えた |

- 最初の版は「アラームのみ」を `INTERRUPTION_FILTER_ALARMS` で作った。Android はこのフィルタで着信モードを内部でサイレントにし
  (`applyZenToRingerMode`)、「車内」と重ねて順に終えると着信モードがサイレントのまま残った。「重要な通知のみ」型 + `ZenPolicy` に変えた
- 「車内」モードは、設定画面からオンにすると設定アプリ自身が着信モードを内部でサイレントにし、オフで戻す(ZenLog の `com.android.settings`)。Volace の動作とは別

### 未検証(今後)
- Pixel 11 Pro でのプロファイル適用とウィジェット配置(端末ロックのため未実施。
  Android バージョン・音声設定とも 9 Pro XL と同一なので差異が出る要素はない)
- 4×1 ウィジェットの配置(4×2 / 1×1 と同一コードパスのため優先度低)
- プロファイルが5件以上のときの 4×2 の2段目表示
- 長期運用での並べ替え・削除

## 9. ビルドとインストール

```sh
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell cmd notification allow_dnd com.hong.volace   # DNDアクセスをadbで付与する場合
```

- Gradle 9.5.0 / AGP 9.3.1 / KSP 2.3.11 / compileSdk 37 の組み合わせで固定している(相性が厳しいので不用意に上げない)
- `versionCode` は端末に入れるビルドごとに上げる。adb は古い versionCode の APK での上書きを拒否するので、
  新しい DB を古いアプリで開いてしまう事故を防げる
- テスト: `./gradlew testDebugUnitTest`
- AGP 9.x は Kotlin プラグインを内蔵しているため、`org.jetbrains.kotlin.android` は**入れてはいけない**

### 9.1 署名(重要)

配布は2台へのサイドロードのみだが、**上書きアップデートを続けるには署名鍵を固定する必要がある**ため、
自己管理の release 鍵を作成した。

| 項目 | 値 |
|---|---|
| キーストア | `~/.android/volace-release.jks`(RSA 4096 / 有効期限 2126年) |
| エイリアス | `volace` |
| パスワード等 | `keystore.properties`(プロジェクト直下・`chmod 600`・`.gitignore` 済み) |
| 証明書 SHA-256 | `813ef666...78ea2ba7` |

`keystore.properties` が無い環境では署名設定を組み立てないだけで、ビルド自体は通る(未署名APKになる)。

**この2ファイルを失うと、既存インストールへの上書きができなくなる**(端末側でアンインストールが必要)。
必ずバックアップすること。

### 9.2 R8 / リソース圧縮

`isMinifyEnabled = true` + `isShrinkResources = true`。APK は 66MB(debug) → **2.35MB**。

`app/proguard-rules.pro` で保持しているもの:
- `com.hong.volace.widget.**` — provider はマニフェスト参照だけでなく `WidgetStyle` が `Class` を保持するため
- `* extends androidx.room.RoomDatabase` — Room は生成実装をクラス名で解決するため

実機検証(2026-08-21): リリースビルドでもアプリ・ウィジェット(配置/タップ/再描画)・
ベクタアイコン・アプリアイコンすべて debug と同一動作。クラッシュなし。
