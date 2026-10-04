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
| ウィジェット | Jetpack Glance | Compose風APIでRemoteViewsを生成、現行の推奨手段 |
| 永続化 | Room | プロファイルのCRUD・並び替えに向く。件数は数個〜十数個想定でオーバースペックにならない |
| 非同期 | Kotlin Coroutines + Flow | Room/Composeとの親和性 |
| minSdk | 33 (Android 13) | 対象端末2台のみ・自己配布のため後方互換を切り捨てて簡素化 |
| targetSdk / compileSdk | 開発時点の最新(35 or 36) | サイドロードのためストア審査要件は無関係。最新APIの制約(通知ポリシー等)にはそのまま追従 |
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
  colorArgb: Int            // アクセントカラー(一覧・ウィジェット共通)
  iconKey: String           // ProfileIcon のキー。未知の値は既定アイコンにフォールバック
```

Room の schema version は **2**。v1 → v2 で `colorArgb` / `iconKey` を `ALTER TABLE ADD COLUMN` する
マイグレーションを持つ(実機で既存データを保持したまま移行できることを確認済み)。

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
- **行タップ = 即時適用**。適用中の行は「プロファイル色の薄い塗り + 同色の枠 + 『適用中』バッジ」で強調
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
- 並べ替えは上部バーの「並べ替え」(並べ替え中は「完了」)でモードを切り替え、各行に ↑↓ ボタンが出る方式。
  常時表示しないのは、行全体をタップ領域として最大化し誤タップを避けるため

### 4.2 プロファイル編集 (ProfileEditScreen)
参考: 添付画像2
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
| 1×1 | なし(場所がない) | 従来どおり循環 |
| 4×1 | 5つ目のタイルに **6本の縦ミニバー**(R N M A V S、アプリ一覧と同じ)+ 着信モードのアイコン | アプリを開く |
| 4×2 | 上段に **横バー + 数値** を 2列×3行(編集画面の並び)+ 着信モードと「変更あり」の文言 | アプリを開く |

- 入口は文字で示す(issue #10。アイコンだけでは伝わらなかった): 4×2 は見出しに「現在の音量」と「アプリを開く ›」、
  4×1 はタイルの下に「音量詳細」。ずれているときは「変更あり」、アクセス許可が無いときは「要許可」に変わる(色付き)
- 「アプリを開く」は `MainActivity` を `NEW_TASK | CLEAR_TOP` で起動するので、アプリが編集画面のまま
  裏にいても、必ずアプリアイコンと同じプロファイル一覧から始まる
- バーは `ClipDrawable` を `src` にした `ImageView` に `setImageLevel(0..10000)` で長さを、
  `setColorFilter` で色(適用中プロファイルの色)を指定している。`ProgressBar` の色付けより素直
- 4×2 は `RemoteViews(Map<SizeF, RemoteViews>)` で高さ 180dp 未満なら音量表示を外した版を出す
  (縮めたときにプロファイルボタンが潰れないように。Pixel 9a の 4×2 は約 200dp)
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
- 期待値は `getStreamMinVolume()`〜`getStreamMaxVolume()` に丸める(通話・アラームは最小 1)
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

## 6. パーミッション

```xml
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
<uses-permission android:name="android.permission.ACCESS_NOTIFICATION_POLICY" />
```
- どちらも通常権限(ランタイムダイアログなし)
- ただし `ACCESS_NOTIFICATION_POLICY` は宣言だけでは不十分で、ユーザーが設定画面から個別に許可する必要がある(4.3のオンボーディングで対応)
- フォアグラウンドサービス・Accessibility Service・Device Admin は不要(すべてタップ起点の即時処理のため)
- 外部での音量変更への追従(5.6)は JobScheduler の content-trigger で、追加の権限は要らない

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
- QS タイルのクリックがユーザー操作扱いかは文書に無いので、タイルからは表示中の Activity を経由して適用する

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
