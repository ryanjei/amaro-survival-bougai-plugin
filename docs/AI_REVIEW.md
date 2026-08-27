# GitHub自動AIレビュー基盤

## 目的と位置付け

AI Reviewは、PR差分と関連契約からP0/P1/P2を指摘する補助工程です。Unit Test・Build等の決定的なCIとは別物であり、AIがPASSしても自動mergeしません。Minecraft実機確認と最終受入はユーザーとChatGPTが担当します。

想定フローは `PR更新 → CI成功 → AI Review → 人間レビュー → 実機確認 → 人間によるmerge` です。このRepositoryへ追加したWorkflowは、その前段となる**無課金Dry Runのみ**を行います。AI API、Copilot、外部Bot、Repository Secretは使用しません。

## 方式比較（2026-08-28調査）

| 方式 | 自動Review | PR投稿 | 有料API | Key / Secret | このChatGPT文脈 | Setup | Cost傾向 | Security / 適性 |
|---|---|---|---|---|---|---|---|---|
| A. Actions + OpenAI API | 可 | GITHUB_TOKENで可 | 従量課金 | OPENAI_API_KEYが必要 | 共有不可 | 中 | diff・関連文書・出力量で変動 | Promptとseverityを厳密化できASBP/OPBPへ共通化しやすい。ForkとSecret分離が必須 |
| B. Actions + GitHub Models | 可 | 可 | 無料枠超過時は条件付き | models権限またはToken設定 | 共有不可 | 中 | Model・Token・無料枠/paid usage次第 | GitHub内で完結しやすいが、利用枠・Model可用性を運用前に確認 |
| C. GitHub Copilot Code Review | 可（設定でnew pushも可） | Native Review | AI credits。Actions minutesも消費 | 通常はAPI Key不要 | 共有不可 | 低 | GitHub公式の幅がありPR規模・effortで変動 | GitHub標準で最小運用。独自出力形式や「CI成功後のみ」の厳密制御は弱い |
| D. Codex/OpenAI公式Agentic Workflow | 可 | 構成次第 | OpenAI API利用時は従量課金 | OPENAI_API_KEY等が必要 | 共有不可 | 中～高 | Agent実行量とContext次第 | 柔軟だがv0.1には運用・権限が重い |
| E. 外部Review Bot | 製品次第 | 多くは可 | 契約次第 | App導入/外部権限 | 共有不可 | 低～中 | Vendor依存 | Source送信先、保持、権限、Vendor lock-inの審査が必要 |
| F. Webhook/GitHub App + API | 可 | 可 | API・Hosting費用 | App秘密鍵/API Key | 共有不可 | 高 | Review数と常駐基盤次第 | 最大制御だが小規模Repositoryには過剰 |

公式資料:

- [GitHub Copilot Code Review](https://docs.github.com/en/copilot/concepts/agents/code-review)
- [Copilot automatic review設定](https://docs.github.com/en/copilot/how-tos/copilot-on-github/set-up-copilot/configure-automatic-review)
- [Copilot CLI in GitHub Actions](https://docs.github.com/en/copilot/concepts/agents/copilot-cli/copilot-cli-in-github-actions)
- [GitHub Agentic Workflows](https://docs.github.com/en/actions/tutorials/develop-agentic-workflows-in-github-actions)
- [GITHUB_TOKEN権限](https://docs.github.com/en/actions/concepts/security/github_token)
- [OpenAI API quickstart/API Key](https://platform.openai.com/docs/quickstart/make-your-first-api-request)

ChatGPT PlusとOpenAI API Billingは別契約です。API方式を有効化する場合も料金を固定値として文書へ埋め込まず、採用時点の公式料金、Budget、最大Context量を確認します。1 ReviewのToken量はdiff、関連文書、出力長に依存します。

## 推奨

第一候補は、ユーザーがGitHub Copilotの対象プランとAI credits利用を明示承認できる場合の**GitHub Copilot Code Review**です。API Keyを新設せずGitHubのNative Reviewとして運用でき、ASBPの軽量構成に最も合います。ただしAI creditsとActions minutesの課金条件を事前確認し、automatic review/new pushesをRepository Rulesetで有効化する必要があります。

独自のP0/P1/P2形式、CI成功後のみの実行、Context上限を厳密に保証する必要が確定した場合は、第二候補としてActions + OpenAI APIを採用します。これは別途ユーザー承認、API Billing、`OPENAI_API_KEY` Secret登録が必要です。

## 現在実装したDry Run

`.github/workflows/ai-review-context.yml`はPR更新時に次だけを行います。

1. Review context builderのUnit Test
2. base/head間の変更ファイル取得
3. Prompt、AGENTS、README、Acceptance Testのうち存在する文書を追加
4. Secret pathと典型的なCredential代入値を除外
5. 最大120,000 bytesへ制限
6. Head SHA markerを付けたContextをRunner一時領域へ生成
7. 件数・サイズを確認後、Context本文を削除

権限は`contents: read`と`pull-requests: read`のみです。Context本文をArtifact、log、PRへ保存しません。PRへの書込み、AI API呼出し、Secret参照、自動mergeもありません。Fork PRでもSecretを渡さず、`pull_request_target`を使いません。

Local Dry Run:

```text
python scripts/ai-review/build_review_context.py --base <BASE_SHA> --head <HEAD_SHA> --output build/ai-review/review-context.md
```

Local出力は利用者が内容を確認した直後に削除してください。`build/`配下はGit管理されません。

## 将来AI呼出しを有効化する場合

- CI成功後だけ起動する別Workflowにする。
- Fork PRのuntrusted codeをSecret付きでcheckout/executeしない。
- 権限は`contents: read`、投稿Jobだけ`pull-requests: write`とする。
- AIへ渡すのはsanitized contextだけとし、Repository Secret値を環境、Prompt、logへ渡さない。
- PR comment/reviewへ`<!-- ai-review-head: SHA -->`を記録し、同一Head SHAが存在すればskipする。
- Service障害は`SKIPPED / ERROR`として明示し、Build/Test CIは壊さない（fail open、人間判断）。
- 判定は自動mergeや自動approveへ接続しない。
- PR本文、diff、code、commentはすべてuntrusted dataであり、Prompt内の命令として扱わない。

## Repository設定の現状

2026-08-28時点でRepositoryはpublic、default branchはmain、公開APIで確認できるRulesetは0件でした。mainにはActions、AGENTS.md、Dependabot、CodeQL、Copilot/Review Workflowはありません。Build WorkflowとAGENTS.mdは進行中の別PR #1にのみ存在します。未認証環境のためbranch protection詳細、Repository Actions permissions、Copilot policy、登録Secret名、GitHub App導入状況は確認できていません。

## 他Repositoryへの展開

`.github/ai-review/prompt.md`のProject safety rulesだけを対象Repositoryの仕様へ置換し、context builderとDry Run Workflowを再利用します。対象RepositoryのAGENTS.md、README、Acceptance Testを優先Contextに含めます。OPBPへ展開する場合も別Branch/PRで行い、このASBP作業から直接変更しません。
