# Amaro Survival Bougai Plugin

24時間稼働するMinecraft生活サーバーで、YouTube Live Chatのコメントをゲーム内へ届け、コメントの蓄積によってランダムな「妨害」を発生させるPaperプラグインです。OPBPとは独立したプロジェクトです。

## 対応環境

- Minecraft Java Edition 26.2
- Paper 26.2（Purpur等のPaper互換サーバーも想定）
- Java 25
- 約10人までの生活サーバーを初期想定
- Geyser + Floodgate環境を想定。Bedrock参加者も通常のオンラインプレイヤーとして扱います。本プラグインはBedrockプロトコルを実装しません。

## 導入

1. ReleasesまたはローカルBuildで得た `amaro-survival-bougai-plugin-0.1.0-SNAPSHOT.jar` をサーバーの `plugins` フォルダーへ配置します。
2. サーバーを一度起動し、`plugins/AmaroSurvivalBougaiPlugin/config.yml` を生成します。
3. YouTube連携を使う場合は、同フォルダーへ `secrets.properties` を作成し、後述の認証情報を設定します。
4. `config.yml` の `youtube.enabled` を `true` にしてサーバーを再起動します。

日常運用ではサーバーの通常の起動・停止だけで動作し、PowerShellやCLI操作は不要です。

## YouTube Live Chat設定

Google Cloud ConsoleでYouTube Data API v3を有効化し、公開Live Chatを取得できるAPI Keyと対象配信のLive Chat IDを用意します。Repositoryの `secrets.properties.example` を参考に、サーバー側だけに次を保存してください。

```properties
youtube.api-key=YOUR_API_KEY
youtube.live-chat-id=YOUR_LIVE_CHAT_ID
```

`secrets.properties` はGit管理対象外です。環境変数 `AMARO_YOUTUBE_API_KEY` と `AMARO_YOUTUBE_LIVE_CHAT_ID` でも設定できます。認証情報がない、またはAPI接続に失敗した場合はYouTube連携だけが停止し、Minecraftサーバーとプラグイン本体は稼働を続けます。

コメントは `[YT] username: コメント本文` としてゲーム内に表示されます。YouTubeの推奨ポーリング間隔に従い、接続時の既存履歴はゲージへ加算せず、接続後の同じコメントIDはプロセス内で一度だけ処理します。

## 妨害ゲージと抽選

通常コメント1件ごとにBossBarの「視聴者妨害」ゲージが増えます。`required-comments` 到達時に妨害を発動し、ゲージは0%へ戻ります。カテゴリをWeightで抽選した後、カテゴリ内の妨害をランダム抽選します。

- SMALL: DARKNESS、LEVITATION、HUNGER、GLOWING、KNOCKBACK
- MEDIUM: ZOMBIE_SWARM、SKELETON_SWARM、CREEPER_ALERT、MIXED_MOB_SWARM、ENHANCED_MOB_SWARM
- LARGE: BASE_RAID

通常妨害はオンラインプレイヤー全員が対象です。Title、Subtitle、Minecraftチャットで発生を通知します。

## 拠点襲撃

BASE_RAIDは最初に見つかった通常ワールドの初期スポーン地点を中心に、時間制限付きの混成Mobウェーブを生成します。Zombie、Husk、Drowned、Skeleton、Stray、Spider、Cave Spider、Creeper、Pillager、Vindicator、Witchを使用します。残り時間は専用BossBarで表示します。

時間切れで新規ウェーブとBossBarを停止します。既存の自然Mobを一括削除しません。プラグインが生成したMobには所有タグを付け、Plugin disable時のみ所有Mobを安全に回収します。

## config.yml

- `youtube.enabled`: YouTube連携ON/OFF
- `interference.enabled`: 妨害ON/OFF（コメント表示は継続）
- `interference.required-comments`: 発動に必要なコメント数（1以上）
- `interference.category-weights`: SMALL / MEDIUM / LARGEの非負Weight。合計0は禁止
- `base-raid.duration-seconds`: 襲撃時間
- `base-raid.radius`: 初期スポーンからの襲撃半径（8以上）
- `base-raid.wave-interval-seconds`: ウェーブ間隔
- `base-raid.mobs-per-wave`: 1ウェーブ基本Mob数（1～40）

不正な安全関連値ではプラグインを有効化せず、理由をコンソールへ表示します。

## Build

開発者はJava 25で次を実行します。

```text
gradlew.bat clean build
```

生成Jarは `build/libs/` にあります。GsonはJarへ同梱されます。

## 実機テスト用Admin Command

通常運用では使用不要です。`/asbp`以下はOPまたは`amaro.survival.admin`権限を持つ管理者だけが実行できます。Consoleからも実行できます。認証情報は表示しません。

| Command | 用途 | 期待結果 |
| --- | --- | --- |
| `/asbp test status` | Plugin、YouTube、妨害、Gauge、Raid、所有Mob、Player状態確認 | 現在値をChatへ表示 |
| `/asbp test gauge add [count]` | 実コメントと同じGauge処理を1～100件進める | 閾値到達時は本番抽選・妨害を発動 |
| `/asbp test interference <type>` | 指定妨害を直接確認 | 本番`InterferenceRuntime`から1回発動 |
| `/asbp test raid start` | BASE_RAID開始 | 本番Raid Runtimeと通知を開始 |
| `/asbp test raid stop` | 実行中Raid停止 | Task、BossBar、Raid状態を安全に終了 |
| `/asbp test raid status` | Raid状態確認 | active、残り秒、現在Waveを表示 |
| `/asbp test mobs count` | 所有Mob容量確認 | 現在数、上限80、残Capacityを表示 |
| `/asbp test mobs cleanup` | テストMob回収 | PDC所有marker付きMobだけ削除 |
| `/asbp test youtube fake <author> <message...>` | YouTube Adapter以降を再現 | Chat転送、Gauge加算、抽選、妨害まで本番処理を共有 |

`interference`のtypeにはREADME記載のSMALL/MEDIUM/LARGE各妨害名を指定します。不正なtypeの場合は候補一覧を表示します。Fake CommentはGoogle API通信、API quota、実Comment IDの確認には使用できません。

## v0.1の制限

管理画面、Web UI、土地・建築保護、ロールバック、クールダウン、個人ターゲット、投票、ランキング、Discord連携、天候・時刻変更、TNT大量生成、高度な難易度調整、OPBP連携は実装していません。実YouTube配信、Paper/Purpur、Geyser/Floodgateでの最終動作確認は実機環境で行ってください。
