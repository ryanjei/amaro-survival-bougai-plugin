# ASBP v0.1 実機受入テスト

Gate 1～4を順番に合格してから、外部認証・別Clientを使用するGate 5へ進む。各Gateで異常があればそこで停止し、`.runtime/logs/launcher-latest.log`、`.runtime/paper/logs/latest.log`、再現手順を保存する。

## Gate 1: 起動・停止・再起動

1. Repository rootの`START_SERVER.bat`をダブルクリックする。
2. Java 25確認、clean build、Unit Test、Paper 26.2 build 112確認、ASBP JAR配置が成功することを確認する。
3. 初回だけMinecraft EULAのリンクを確認し、同意する場合のみ`Y`を入力する。`N`ではPaperが起動しないことを確認する。
4. Minecraft Java Editionから`localhost`へ接続する。
5. ConsoleでASBPがenableされ、YouTube設定不足時もPaperが稼働を続けることを確認する。
6. Launcher Windowで`Y`を押し、Paperへ`stop`が送られ、World保存、Plugin disable、終了コード0となることを確認する。
7. 再度起動し、World、`config.yml`、`secrets.properties`が上書きされず維持されることを確認する。

不合格条件: Build失敗後にPaperが起動する、古いPlugin JARが使われる、EULAが無断承認される、強制killされる、他Plugin JARが消える。

## Gate 2: Admin Test Command

OPまたは`amaro.survival.admin`権限で次を確認する。一般Playerでは拒否され、ConsoleでもPlayerを必要としない操作が実行できることを確認する。

1. `/asbp test status`
2. Plugin enabled、YouTube/妨害設定、Gauge、Raid、ASBP所有Mob、残Capacity、Online Player数が正しいことを確認する。
3. Secretが表示されないことを確認する。
4. 不正type、`gauge add 0`、負数、非数値、101以上が安全に拒否されることを確認する。

## Gate 3: Gauge・Fake YouTube・通常妨害

### Fake YouTube / Gauge

1. `/asbp test youtube fake testuser hello world`を実行する。
2. `[YT] testuser: hello world`がMinecraft Chatへ表示され、Gaugeが1件増えることを確認する。
3. `/asbp test gauge add [count]`で`required-comments`へ到達させる。
4. Weight抽選された妨害が1回発動し、Gaugeが0へ戻ることを確認する。

### SMALL

`/asbp test interference <type>`で次を1回ずつ確認する。

- DARKNESS
- LEVITATION
- HUNGER
- GLOWING
- KNOCKBACK

全オンラインPlayerへTitle、Subtitle、Chat通知と妨害が適用され、過剰な落下・速度にならないことを確認する。

### MEDIUM

- ZOMBIE_SWARM
- SKELETON_SWARM
- CREEPER_ALERT
- MIXED_MOB_SWARM
- ENHANCED_MOB_SWARM

各Player周辺の安全な位置へ生成され、ASBP所有Mob合計が80体を超えないことを確認する。

## Gate 4: Base Raid・所有Mob・Cleanup・Shutdown

1. `/asbp test raid start`で初期スポーン周辺にRaidが開始し、Title、Chat、残り時間BossBarが表示されることを確認する。
2. `/asbp test raid status`でactive、残り秒、Waveが更新されることを確認する。
3. 複数WaveのMob構成が混成で、初期スポーン半径内に生成されることを確認する。
4. `/asbp test mobs count`で上限80と残Capacityを確認する。
5. 自然Mobを用意してから`/asbp test mobs cleanup`を実行し、ASBP所有Mobだけが削除されることを確認する。
6. `/asbp test raid stop`で新規WaveとRaid BossBarが停止することを確認する。
7. stop後に再startし、初回Waveが再び生成されることを確認する。
8. Raid中にLauncherの`Y`でPaperを停止し、Task/BossBar/Pollerが終了し、ASBP所有Mobだけがcleanupされることを確認する。

### Config

バックアップ後、次を1項目ずつ変更して再起動する。

- `required-comments`の変更がGaugeへ反映される。
- `interference.enabled=false`ではFake CommentをChat表示するがGauge・妨害を進めない。
- 必要コメント数0、Weight合計0、Raid半径8未満、Wave Mob数41等の不正安全値でASBPがfail-safeとなり、理由がlogに出る。

## Gate 5: 実YouTube Live Chat・Geyser・Floodgate

Gate 1～4合格後に実施する。

1. Git管理外の`secrets.properties`へAPI KeyとLive Chat IDを設定し、`youtube.enabled=true`で再起動する。
2. 接続後の新規コメントがChatへ1回表示され、Gaugeへ1回加算されることを確認する。
3. 同じComment IDが再取得されても二重加算されないことを確認する。
4. Plugin接続前の既存コメント履歴がGaugeへ加算されないことを確認する。
5. API停止・無効認証時にYouTube連携だけが停止し、PaperとASBPの他機能が継続することを確認する。
6. Geyser/Floodgate経由のBedrock PlayerがOnline Player数へ含まれ、SMALL/MEDIUM妨害、Title、BossBarの対象になることを確認する。

Geyser/Floodgate本体の導入・認証・互換性問題はASBPの独自プロトコル実装ではなく、Server構成として切り分ける。
