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
4. 起動時から自動接続する場合は、`config.yml` の `youtube.enabled` を `true` にします。`false`でも、配信開始時に`/asbp youtube on`で接続できます。

日常運用ではサーバーの通常の起動・停止だけで動作し、PowerShellやCLI操作は不要です。

## YouTube Live Chat設定

Google Cloud ConsoleでYouTube Data API v3を有効化し、公開Live Chatを取得できるAPI Keyと対象配信のLive Chat IDを用意します。Repositoryの `secrets.properties.example` を参考に、サーバー側だけに次を保存してください。

```properties
youtube.api-key=YOUR_API_KEY
youtube.live-chat-id=YOUR_LIVE_CHAT_ID
```

`secrets.properties` はGit管理対象外です。環境変数 `AMARO_YOUTUBE_API_KEY` と `AMARO_YOUTUBE_LIVE_CHAT_ID` でも設定できます。認証情報がない、または初期化に失敗した場合はYouTube Runtimeを停止状態に保ち、Minecraftサーバーとプラグイン本体は稼働を続けます。

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

- `youtube.enabled`: Plugin起動時にYouTube Pollingを自動開始するか
- `interference.enabled`: Plugin起動時に自動妨害を有効状態にするか
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

## ローカル実機確認

`START_SERVER.cmd` は、準備済みのローカルPaperサーバーを起動するだけのLauncherです。PluginのBuild・配置・設定変更・YouTube制御は行いません。停止時はPaper Consoleで `stop` と入力してください。

初回準備と確認は、次の順で行います。

1. コマンドプロンプトで `java -version` を確認し、Java 25が選択されるようにします。LauncherもJavaのMajor Versionを検証し、25以外では起動しません。
2. Repository直下で `gradlew.bat clean build` を実行します。
3. 生成された `build/libs/amaro-survival-bougai-plugin-0.1.0-SNAPSHOT.jar` を `.runtime/paper/plugins/` に配置します。
4. Paper 26.2 build 112の公式Jarを `.runtime/paper/paper.jar` として配置します。Paper Jar、World、Log、Server設定、外部Plugin、EULA、Player情報は `.runtime/` 配下のローカルデータであり、GitへCommitしません。
5. 初回起動前にMinecraft EULAを確認し、同意する場合だけ `.runtime/paper/eula.txt` をユーザー自身で設定します。LauncherはEULAへ自動同意しません。
6. `START_SERVER.cmd` をダブルクリックします。ServerのWorking Directoryは `.runtime/paper`、JVM設定は `-Xms2G -Xmx4G`、起動引数は `nogui` です。エラー終了時はWindowが閉じず、原因を確認できます。
7. Minecraft Java Editionから `localhost:25565` へ接続します。
8. Paper Consoleで `op <Minecraft名>` を実行し、実機確認担当者へOP権限を付与します。
9. Paper Consoleまたは `plugins` Commandで `AmaroSurvivalBougaiPlugin` が有効になっていることを確認します。
10. `/asbp test status` でPlugin、YouTube、妨害、Gauge、Raid、所有Mob、Online Playerを確認します。
11. `/asbp test youtube fake testuser hello` でYouTube Adapter以降のChat表示とGauge加算を確認します。妨害RuntimeがOFFなら先に `/asbp interference on` を実行します。
12. `/asbp test gauge add 1` と `/asbp test gauge add 9` でGauge進行と閾値到達時の抽選・リセットを確認します。
13. SMALL各種を `/asbp test interference DARKNESS` 等で直接確認します。
14. MEDIUM各種を `/asbp test interference ZOMBIE_SWARM` 等で直接確認し、`/asbp test mobs count` で上限80を確認します。終了後は必要に応じて `/asbp test mobs cleanup` を使用します。
15. `/asbp test raid start` でBASE_RAIDを開始し、残り時間・Wave・初期Spawn周辺での生成を確認します。`/asbp test raid status`、`/asbp test raid stop`、再度 `start` の順で再実行も確認します。
16. `secrets.properties` をServer側Plugin Directoryへ設定後、`/asbp youtube on` で実YouTube接続を開始し、`status`、実Comment、`off`、再度 `on` を確認します。秘密情報はGitへCommitしません。
17. Geyser/Floodgateを利用する場合は、それぞれの公式手順で `.runtime/paper/plugins/` へ導入し、Bedrock側からLocal/LAN接続、Chat表示、SMALL/MEDIUM妨害、BASE_RAID中の動作を確認します。本PluginはGeyser/Floodgate Jarの取得や設定変更を行いません。

Fresh Cloneでは `.runtime/` が存在しないため、上記のPaper Jar・Plugin Jar・EULA・必要なServer設定を準備してからLauncherを実行してください。実Worldや既存Server設定をRepositoryへコピーしないでください。

## 管理Commandと実機テスト用Command

`/asbp`以下はOPまたは`amaro.survival.admin`権限を持つ管理者だけが実行できます。Consoleからも実行でき、認証情報は表示しません。YouTubeと自動妨害は独立してON/OFFできます。自動妨害OFF中もYouTubeコメント表示は継続しますが、Gaugeへ加算しません。

| Command | 用途 | 期待結果 |
| --- | --- | --- |
| `/asbp youtube on` | YouTube Polling開始 | 再起動なしでPollerを1個だけ開始 |
| `/asbp youtube off` | YouTube Polling停止 | Minecraft Serverを止めずPollerをclose |
| `/asbp youtube status` | 実YouTube Runtime確認 | running/stoppedとAuto Startを分離表示 |
| `/asbp interference on` | 自動妨害を有効化 | 次のコメントからGauge加算を再開 |
| `/asbp interference off` | 自動妨害だけ停止 | コメント表示と既存Gaugeを維持し、新規加算を停止 |
| `/asbp interference status` | 実妨害Runtime確認 | enabled/disabledとAuto Startを分離表示 |
| `/asbp test status` | Plugin、YouTube、妨害、Gauge、Raid、所有Mob、Player状態確認 | 現在値をChatへ表示 |
| `/asbp test gauge add [count]` | 実コメントと同じGauge処理を1～100件進める | 閾値到達時は本番抽選・妨害を発動 |
| `/asbp test interference <type>` | 指定妨害を直接確認 | 本番`InterferenceRuntime`から1回発動 |
| `/asbp test raid start` | BASE_RAID開始 | 本番Raid Runtimeと通知を開始 |
| `/asbp test raid stop` | 実行中Raid停止 | Task、BossBar、Raid状態を安全に終了 |
| `/asbp test raid status` | Raid状態確認 | active、残り秒、現在Waveを表示 |
| `/asbp test mobs count` | 所有Mob容量確認 | 現在数、上限80、残Capacityを表示 |
| `/asbp test mobs cleanup` | テストMob回収 | PDC所有marker付きMobだけ削除 |
| `/asbp test youtube fake <author> <message...>` | YouTube Adapter以降を再現 | Chat転送、Gauge加算、抽選、妨害まで本番処理を共有 |

`test interference`のtypeにはREADME記載のSMALL/MEDIUM/LARGE各妨害名を指定します。自動妨害RuntimeがOFFでも管理者の手動テストは実行できます。不正なtypeの場合は候補一覧を表示します。Fake Commentは通常コメント入力経路を通るため、自動妨害OFF中は表示のみでGaugeへ加算しません。Google API通信、API quota、実Comment IDの確認には使用できません。

## v0.1の制限

管理画面、Web UI、土地・建築保護、ロールバック、クールダウン、個人ターゲット、投票、ランキング、Discord連携、天候・時刻変更、TNT大量生成、高度な難易度調整、OPBP連携は実装していません。実YouTube配信、Paper/Purpur、Geyser/Floodgateでの最終動作確認は実機環境で行ってください。
