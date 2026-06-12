# Requirements Document

## Introduction

feedman-android は SPEC §4 で定義されたサーバー API を唯一の正本として扱い、後続 Issue
（#17 以降の Retrofit / Repository / Paging / 画面）はここで定義されるドメインモデルと
fixture JSON を前提に実装される。本 Issue では SPEC §4.2 に列挙された全モデル
（`CrossFeedItem` / `ItemSummary` / `ItemDetail` / `StarredItemSummary` / `ItemSearchHit` /
`Subscription` / `User` および §4.1 のページ envelope）をシリアライズ可能なドメイン型として
core/model に確立し、SPEC §4.2 の実形に即した fixture JSON で decode 検証する。これにより
プロトタイプの `design/mobile/fm-data.jsx` モック形ではなく SPEC §4 を契約の正本とする方針
（SPEC §9）を実装レベルで固定する。

## Requirements

### Requirement 1: API ドメインモデルの網羅

**Objective:** As a Android アプリ開発者, I want SPEC §4.2 の全 API モデルに対応する
シリアライズ可能なドメイン型, so that 後続 Issue（#17 以降）の Retrofit / Repository 実装が
合意された契約に直接依存できる

#### Acceptance Criteria

1. The Feedman Domain Model module shall expose SPEC §4.2 で定義された `CrossFeedItem` /
   `ItemSummary` / `ItemDetail` / `StarredItemSummary` / `ItemSearchHit` / `Subscription` /
   `User` に対応する型を 1 つずつ持つ。
2. The Feedman Domain Model module shall expose SPEC §4.1 の一覧系共通レスポンス envelope
   （`items` / `next_cursor` / `has_more`）を表現する汎用ページ型を 1 つ持つ。
3. Where 横断新着タイムラインのレスポンスが対象, the Feedman Domain Model module shall
   SPEC §4.1 の `since_time`（RFC3339）を保持できる envelope を提供する。
4. The Feedman Domain Model module shall SPEC §4.2 の各モデルが宣言する全フィールドを
   field 名・nullable 性を保ったまま保持する。
5. The Feedman Domain Model module shall `ItemSearchHit` を `ItemSummary` から派生させず、
   SPEC §4.2 の差分（`hatebu_fetched_at` を含まない / `feed_title` を含む /
   `favicon_url` が nullable / `published_at` が nullable）をそのまま表現する。

### Requirement 2: SPEC §4.2 準拠の fixture JSON

**Objective:** As a Android アプリ開発者, I want SPEC §4.2 の実形に即した fixture JSON 集,
so that decode 検証と後続 Issue のテスト基盤が同一の合意済みサンプルを参照できる

#### Acceptance Criteria

1. The Feedman Test Fixtures shall Requirement 1 で定義された各モデル
   （`CrossFeedItem` / `ItemSummary` / `ItemDetail` / `StarredItemSummary` / `ItemSearchHit` /
   `Subscription` / `User`）について少なくとも 1 件の fixture JSON を含む。
2. The Feedman Test Fixtures shall 一覧系 envelope について `has_more: true` と
   `has_more: false` の双方の fixture を含む。
3. The Feedman Test Fixtures shall 横断新着タイムラインのページ envelope について
   `since_time` を含む fixture を含む。
4. The Feedman Test Fixtures shall `feed_favicon_url` / `favicon_url` について SPEC §4.4 の
   data URL 形式（`data:<mime>;base64,...`）と `null` の双方の fixture バリアントを含む。
5. The Feedman Test Fixtures shall `is_date_estimated: true` と `is_date_estimated: false`
   の双方のケースを含む。

### Requirement 3: シリアライズ仕様の検証

**Objective:** As a Android アプリ開発者, I want fixture JSON が決定的に decode できることを
自動テストで担保, so that SPEC §4 と実装の乖離を CI で早期検知できる

#### Acceptance Criteria

1. When Requirement 2 の各 fixture JSON が Requirement 1 のモデルへ decode される,
   the Feedman Domain Model Decoder shall 例外なく成功する。
2. When `feed_favicon_url` が `null` の fixture と data URL 形式の fixture を decode する,
   the Feedman Domain Model Decoder shall いずれも成功し、nullable 表現を保つ。
3. When `ItemSearchHit` の fixture を decode する, the Feedman Domain Model Decoder shall
   `hatebu_fetched_at` の有無に依存せず成功し、`feed_title` / nullable `favicon_url` /
   nullable `published_at` を保持する。
4. When 一覧系 envelope の `has_more: false` かつ `next_cursor: null` の fixture を decode する,
   the Feedman Domain Model Decoder shall 終端を表現できる状態に正しくマップする。
5. When fixture JSON にモデル未定義の追加キーが含まれている, the Feedman Domain Model
   Decoder shall decode を失敗させず、既知フィールドの値を保持する。
6. When 日時フィールド（`published_at` / `created_at` / `hatebu_fetched_at` /
   `since_time` 等）の値が RFC3339 文字列である, the Feedman Domain Model Decoder shall
   元の文字列表現を欠落なく保持する。
7. If fixture JSON に nullable と宣言されたフィールドが欠落している, the Feedman Domain
   Model Decoder shall それを `null` 相当として扱い decode を失敗させない。

## Non-Functional Requirements

### NFR 1: 変更範囲とトレーサビリティ

1. The Feedman Domain Model module shall 本 Issue で追加・変更するソースコードを
   `core/model` 配下と `app/src/test/` 配下（fixture JSON 含む）のみに限定する。
2. The Feedman Test Fixtures shall fixture JSON を `app/src/test/resources/fixtures/` 配下に
   集約する。
3. The Feedman Domain Model module shall フィールド名・nullable 性・必須/任意の区分を
   SPEC §4.2 の宣言から逸脱させない。

### NFR 2: 検証容易性

1. The Feedman Domain Model Decoder shall Requirement 3 の各 AC を JVM 単体テストで
   再現可能にする（エミュレータを必要としない）。

## Out of Scope

- Retrofit インターフェース定義および HTTP クライアント実装（#17 以降）
- Repository / Paging 実装（#17 以降）
- UI 層（ViewModel / Compose 画面）でのモデル利用
- 認証・トークン管理・エラーハンドリング戦略（別 Issue）
- 既存 `core/model/Item.kt`（#1 で導入されたモック用 Item）と本 Issue の API モデルの
  統合・置き換え方針の決定（実装詳細として後続タスクで判断）
- SPEC §4.2 に列挙されていないモデル（例: `Feed` 詳細・キーワード通知系）の追加
- `design/mobile/fm-data.jsx` のモック JSON 形を fixture として採用すること（SPEC §9 に従い不採用）

## Open Questions

- なし（SPEC §4.2 / §4.4 / GRAND-DESIGN §3 に契約・配置が明示されており、本 Issue に必要な
  情報は揃っている）

## 関連

- Parent: #2
- Depends on: #1
