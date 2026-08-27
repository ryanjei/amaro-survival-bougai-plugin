# ASBP v0.1 実機受入テスト

Gate 1～4を順番に合格してから、外部認証・別Clientを使用するGate 5へ進む。各Gateで異常があればそこで停止し、`.runtime/logs/launcher-latest.log`、`.runtime/paper/logs/latest.log`、再現手順を保存する。

## Gate 1: 起動・停止・再起動

1. Repository rootの`START_SERVER.bat`をダブルクリックする。
2. Java 25確認、clean build、Unit Test、Paper 26.2 build 112確認、ASBP JAR配置が成功することを確認する。
3. Geyser 2.11.2 build 1233、Floodgate 2.2.5 build 138、ViaVersion 5.10.0、ViaBackwards 5.10.0が固定版・SHA-256検証付きで配置されることを確認する。
4. 初回だけMinecraft EULAのリンクを確認し、同意する場合のみ`Y`を入力する。`N`ではPaperが起動しないことを確認する。
5. Minecraft Java Editionから`localhost`へ接続する。
6. Consoleまたは`latest.log`でAmaroSurvivalBougaiPlugin、Geyser、Floodgate、ViaVersion、ViaBackwardsがすべて正常enableされ、Plugin load errorがないことを確認する。
7. YouTube設定不足時もPaperが稼働を続けることを確認する。
8. Launcher Windowで`Y`を押し、Paperへ`stop`が送られ、World保存、Plugin disable、終了コード0となることを確認する。
9. `Ctrl+C`とLauncher例外時も、Paper稼働中なら同じstop送信・保存待機経路へ収束することを確認する。
10. stop送信失敗時は強制killせず、Paperがまだ稼働中であることを30秒ごとに明示し、Process終了後も正常停止扱いにならないことを確認する。
11. 再度起動し、World、ASBP/Geyser/Floodgate/ViaVersion/ViaBackwardsの各config、`key.pem`、`secrets.properties`が上書きされず維持されることを確認する。
12. `plugins`へ無関係なテスト用JARを置いて再起動し、Launcherが削除・変更しないことを確認する。

不合格条件: Build失敗後にPaperが起動する、古いPlugin JARが使われる、EULAが無断承認される、強制killされる、他Plugin JARが消える。

## Gate 2: Admin Test Command

初回Launcherで明示指定したPlayer、OP、`amaro.survival.admin`権限、Consoleで次を確認する。指定されていない一般Playerは拒否されることを確認する。指定PlayerはPaper ConsoleでOPを付与せず実行でき、初回成功後は保存UUIDで認可されることを確認する。

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

## Gate 5: 実YouTube Live Chat・Java / Bedrock互換

Gate 1～4合格後に実施する。

1. Git管理外の`secrets.properties`へAPI KeyとLive Chat IDを設定し、`youtube.enabled=true`で再起動する。
2. 接続後の新規コメントがChatへ1回表示され、Gaugeへ1回加算されることを確認する。
3. 同じComment IDが再取得されても二重加算されないことを確認する。
4. Plugin接続前の既存コメント履歴がGaugeへ加算されないことを確認する。
5. API停止・無効認証時にYouTube連携だけが停止し、PaperとASBPの他機能が継続することを確認する。
6. Java Edition 26.2からTCP `25565`へ接続できることを確認する。
7. Geyser 2.11.2 build 1233が対応するBedrock 26.40からUDP `19132`へ接続でき、更新不足警告が出ないことを確認する。
8. Java Edition Accountを持たないBedrock AccountがFloodgate経由で参加できることを確認する。
9. Bedrock Playerが`/asbp test status`のOnline Player数へ含まれ、SMALL妨害、MEDIUM妨害のPlayer周辺生成、Title、BossBarの対象になることを確認する。
10. BASE_RAID中もBedrock Playerが通常Playerとして行動でき、切断・再接続でASBP errorが発生しないことを確認する。
11. ViaBackwards 5.10.0の対応対象から少なくとも1つの旧Java Client versionで接続を確認する。
12. Server 26.2より新しいJava Clientは、ViaVersion 5.10.0または後続の受入済み固定版がそのversionを正式対応した時点で確認する。存在しない未来versionは対象にしない。

Geyser/Floodgate/ViaVersion/ViaBackwards本体の導入・認証・互換性問題はASBPの独自プロトコル実装ではなく、Server構成として切り分ける。Local実機確認はLAN内を基本とし、Router port forward、Firewall、本番Server公開は別作業とする。
