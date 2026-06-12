# Requirements Document

## Introduction

Feedman Android では記事の元ソースを読む動線として外部リンクを Chrome Custom Tabs で開く必要がある。
本 Issue では、記事詳細シートのフッター主アクション「元記事を開く」とタイムラインカードの外部リンクアイコンの両方から、
共通の `LinkOpener` 抽象を経由して当該 URL を開けるようにする。
SPEC §6 で定めるとおり外部リンクを開いた時点で当該記事を既読化し、画面間で表示状態が整合する状態にする。
Custom Tabs 非対応端末では標準 `ACTION_VIEW` インテントへフォールバックし、未対応スキームでは安全側に倒してユーザーへエラーを通知する。
将来の「完全外部ブラウザ切替設定」を見据え、横断関心事として LinkOpener 抽象のみを用意する（設定 UI は本 Issue 対象外）。

## Requirements

### Requirement 1: 記事詳細シートからの元記事閲覧

**Objective:** As a 通勤中に記事を読むユーザー, I want 記事詳細シートのフッターから元記事を一発で開きたい, so that 本文プレビューから本サイトの全文閲覧へスムーズに遷移できる

#### Acceptance Criteria

1. When ユーザーが記事詳細シートのフッター「元記事を開く」を押下したとき, the Article Detail Sheet shall 当該記事の元記事 URL を Custom Tabs で開く
2. When ユーザーが記事詳細シートのフッター「元記事を開く」を押下したとき, the Article Detail Sheet shall 当該記事を既読状態へ即時遷移させ、画面上の既読表示（不透明度低下）に反映する
3. While 元記事の起動操作が進行中, the Article Detail Sheet shall 同じアクションの重複起動を抑止する
4. If 既読化のサーバー反映に失敗したとき, the Article Detail Sheet shall 当該記事の既読状態を元に戻し、ユーザーが認識できるエラーメッセージを表示する

### Requirement 2: タイムラインカードからの元記事閲覧

**Objective:** As a 一覧を流し見しているユーザー, I want カードの外部リンクアイコンから記事詳細シートを開かずに本サイトを開きたい, so that 興味のある記事をワンタップで本文に直行できる

#### Acceptance Criteria

1. When ユーザーがタイムラインカードの外部リンクアイコンを押下したとき, the Timeline Card shall 当該記事の元記事 URL を Custom Tabs で開く
2. When ユーザーがタイムラインカードの外部リンクアイコンを押下したとき, the Timeline Card shall 当該記事を既読状態へ即時遷移させ、当該カードを既読表示（不透明度低下）に切り替える
3. When ユーザーがタイムラインカードの外部リンクアイコンを押下したとき, the Timeline Card shall 同一カードのタップで開く記事詳細シートを起動しない
4. If 既読化のサーバー反映に失敗したとき, the Timeline Card shall 当該カードの既読状態を元に戻し、ユーザーが認識できるエラーメッセージを表示する

### Requirement 3: Custom Tabs 非対応端末でのフォールバック

**Objective:** As a Custom Tabs 非対応ブラウザしか持たない端末のユーザー, I want 元記事を標準ブラウザで開きたい, so that ブラウザ環境に依存せず元記事を読める

#### Acceptance Criteria

1. If 端末に Custom Tabs 対応ブラウザがインストールされていないとき, the Link Opener shall 標準のブラウザ起動インテント経由で同じ URL を開く
2. When フォールバック経由でブラウザが起動したとき, the Link Opener shall Custom Tabs 経由の場合と同じく当該記事を既読化する
3. If フォールバック経由でも URL を開けるアプリが端末に存在しないとき, the Link Opener shall 元記事を開けなかった旨のエラーメッセージを表示し、当該記事の既読化を行わない

### Requirement 4: 未対応スキームの拒否

**Objective:** As a セキュリティを気にするユーザー, I want 不正・未対応のスキームを安全に拒否してほしい, so that 意図しないアプリ起動やインテント注入から保護される

#### Acceptance Criteria

1. If 元記事 URL のスキームが `http` または `https` 以外のとき, the Link Opener shall ブラウザ起動を行わず、ユーザーが認識できるエラーメッセージを表示する
2. If 元記事 URL が空文字または不正な構文のとき, the Link Opener shall ブラウザ起動を行わず、ユーザーが認識できるエラーメッセージを表示する
3. When 未対応 URL によりブラウザ起動が拒否されたとき, the Link Opener shall 当該記事の既読状態を変更しない

### Requirement 5: 既読状態の画面間整合

**Objective:** As a 複数画面を行き来するユーザー, I want どの画面で既読化が走っても他の画面に即時反映してほしい, so that 既に開いた記事を二度同じものとして扱わずに済む

#### Acceptance Criteria

1. When ユーザーが元記事を開いて既読化が成立したとき, the Cross-Screen Read State shall 同じ記事を表示中の他画面（タイムライン／フィード別／スター／検索）の既読表示にも反映する
2. While 既読化のサーバー反映が進行中, the Cross-Screen Read State shall 楽観的に既読状態を表示する
3. If 既読化のサーバー反映が失敗したとき, the Cross-Screen Read State shall 反映した全画面で既読状態を元に戻す

## Non-Functional Requirements

### NFR 1: 外観の一貫性

1. The Custom Tabs Toolbar shall アプリのテーマ（ライト／ダーク）に追従したツールバー色で表示される
2. When ユーザーが端末テーマを切り替えたうえで元記事を開いたとき, the Custom Tabs Toolbar shall 切替後のテーマに整合した色で表示される

### NFR 2: 操作応答性

1. When ユーザーが元記事起動アクションを押下したとき, the Link Opener shall 押下から 300 ミリ秒以内に Custom Tabs もしくはフォールバック起動アクションを発火する
2. The Link Opener shall ユーザーへのエラーフィードバックを 1 秒以内に表示する

### NFR 3: テスト容易性

1. The Link Opener shall URL バリデーション・既読化呼び出し・起動アクション要求の決定ロジックを Android 実機依存（Activity / Intent / Custom Tabs SDK）から分離し、JVM 単体テストで検証可能にする
2. The Link Opener shall Custom Tabs 起動可否判定の結果を入力として受け取り、フォールバック経路選択を JVM 単体テストで検証可能にする

## Out of Scope

- 完全外部ブラウザ（標準 `ACTION_VIEW`）への切替を選ぶ設定 UI（次フェーズ。本 Issue では `LinkOpener` 抽象のみ用意）
- リーダーモード／本文インライン表示の拡張（SPEC §5.4 で不採用）
- Custom Tabs のセッションウォームアップ・プリレンダリングなどパフォーマンス最適化
- 検索結果画面およびスター一覧画面からの外部リンクアイコン起動（本 Issue は詳細シートとタイムラインカードに限定。両画面が同じ `LinkOpener` を呼び出せる抽象を整える時点で十分とする）
- フィード別画面の状態バナーや購読設定など、本機能と直接関係しない既読化トリガ

## Open Questions

- なし

