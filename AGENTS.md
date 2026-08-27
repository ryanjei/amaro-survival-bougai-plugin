# Codex 作業規約

このRepositoryは、24時間稼働するMinecraft生活サーバー向けの軽量なYouTube妨害Paper Plugin「Amaro Survival Bougai Plugin（ASBP）」である。

## 役割分担

ChatGPTは仕様策定、設計、検証観点整理、Codex指示書作成、コードレビュー、実機テスト項目策定、受入判定を担当する。

Codexは依頼範囲の実装、必要なテスト追加、関連テスト実行、build、実装時エラー修正、変更範囲に限定したセルフレビューを担当する。

## GitHub Flow

- `main`は受入済みコードだけを置く。
- 新機能は`feature/*`、不具合修正は`fix/*`、開発・CI・文書基盤は`chore/*`で作業する。
- Codexは原則として`main`へ直接実装・Commitしない。
- 作業BranchをPushし、ChatGPTの受入判定前にmainへmergeしない。
- force push、rebase、history rewriteは明示依頼がない限り行わない。

## Codex使用量最適化

- 指示された範囲だけを実装する。
- 全面リファクタリング、将来機能の先行実装、不要な文書大量生成を行わない。
- 同種処理を新設する前にRepository全体を横断検索する。
- 既存コードとテスト補助を可能な限り再利用する。
- 不具合修正では可能な限り再現テストまたは回帰テストを追加する。

## テストとセルフレビュー

原則として、変更箇所の単体テスト、関連テスト、buildの順で実行する。毎回Fresh Clone、Full E2E、実Paper起動を繰り返さない。Launcherや実機基盤そのものを変更する節目だけ、必要な範囲でLauncher確認を行う。

セルフレビューは今回変更した処理、同種実装、横展開漏れ、未修正箇所、直接的な回帰可能性に限定する。

## ASBP固有ルール

ASBPは次の軽量構成を維持する。

- Paper Plugin
- YouTube Live Chat連携
- コメントGauge
- 妨害
- Base Raid
- Admin Test Command

OPBPの製品仕様や、Web管理画面、Map管理、Game Session、World Template、World Recovery、外部DB、Node.js常駐process、OPBP固有API・ゲーム進行、不要なHTTP Serverを持ち込まない。

- YouTube通信をMinecraft main threadで実行しない。
- Bukkit/Paper API操作はPaper main threadで行う。
- Secretをsource、Git、console、launcher logへ出さない。
- ASBP生成MobだけをPDC ownershipで識別・cleanupし、自然Mobや他Plugin Mobを変更しない。
- Scheduler、BossBar、YouTube Poller、RaidはPlugin disable時に安全に停止する。
- ユーザー向け表示とREADMEは日本語を基本とする。

## 作業報告

Checkpoint・完了報告・レビュー依頼は、ユーザーが一度で転送できる1つのMarkdownコードブロックへまとめる。
