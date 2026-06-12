# Requirements Document

## Introduction

Feedman Android アプリのナビゲーションの骨格となる「アプリシェル」を正式化する Issue で
ある。SPEC §5.0 / GRAND-DESIGN §5.6 にもとづき、左ドロワー・アプリ枠・上部アプリバーから
成るシェルと、`timeline` / `feed/{feedId}` / `starred` / `search` の 4 ルートを宣言する
ナビゲーション基盤をひとつだけ持つ構成へ置き換える。記事詳細・フィード登録・購読設定・
アカウントなどはルートではなくボトムシートで重ねるため、ここではルート定義に含めない。

加えて、未認証時にシェルごとログイン画面に差し替えることで、後続 Issue で SessionState
や認証経路が本格化したときに UI 入口を一箇所で管理できるようにする。本 Issue 完了後、
#30 でドロワーのフィード一覧、#31 で上部アプリバー右側のアクションが順次充填される
前提とする。

## Requirements

### Requirement 1: シェル構造とルート定義

**Objective:** As a エンドユーザー, I want アプリ起動時に共通のドロワー付きシェルから 4 つの
主要ルートへ遷移できるようにしたい, so that 新着タイムライン・フィード別記事一覧・お気に入り・
検索のあいだを破綻なく行き来できる

#### Acceptance Criteria

1. When ログイン済みでアプリを起動したとき, the App Shell shall `timeline` ルートを初期表示
   としてドロワー付きシェル内に描画する
2. The App Shell shall `timeline` / `feed/{feedId}` / `starred` / `search` の 4 ルートのみを
   ナビゲーション基盤に宣言し、それ以外のルートを宣言しない
3. When ユーザーが `feed/{feedId}` ルートへ遷移するとき, the App Shell shall パスパラメータ
   `feedId` をルート引数として受け取り、その値を遷移先画面へ渡す
4. While いずれかのルートを表示しているあいだ, the App Shell shall 同一の上部アプリバーと
   ドロワーをルート切り替えにまたいで保持する
5. The App Shell shall 記事詳細・フィード登録・購読設定・アカウントをルートとして
   宣言せず、ボトムシート前提の入口として後続 Issue に委ねる

### Requirement 2: ドロワーの開閉と遷移挙動

**Objective:** As a エンドユーザー, I want 上部アプリバーのメニューからドロワーを開閉し、
ドロワー内の項目から目的の画面に移動したい, so that 主要画面への入口を一貫した操作で見つけ
られる

#### Acceptance Criteria

1. When ユーザーが上部アプリバー左端のメニューボタンを押下したとき, the App Shell shall
   ドロワーを開き、背後コンテンツに半透明のスクリム（遮蔽レイヤー）を重ねる
2. When ユーザーがスクリム領域をタップしたとき, the App Shell shall ドロワーを閉じ、
   現在のルートを変更しない
3. When ユーザーがドロワーを左方向へスワイプして閉じたとき, the App Shell shall ドロワーを
   閉じ、現在のルートを変更しない
4. When ユーザーがドロワー内の「すべての新着」項目を選択したとき, the App Shell shall
   `timeline` ルートへナビゲートし、同時にドロワーを閉じる
5. When ユーザーがドロワー内の「お気に入り」項目を選択したとき, the App Shell shall
   `starred` ルートへナビゲートし、同時にドロワーを閉じる
6. While ドロワーが開いているあいだ, the App Shell shall ドロワー本体がスクリムよりも前面に
   表示され、ドロワー外コンテンツへの操作入力を遮断する

### Requirement 3: 未認証状態でのシェル差し替え

**Objective:** As a 未認証ユーザー, I want セッションが確立していない状態ではログイン画面
だけが表示されるようにしてほしい, so that ドロワー越しに認証必須の画面へ誤って到達しない

#### Acceptance Criteria

1. While セッション状態がログイン未確立（LoggedOut 相当）であるあいだ, the App Shell shall
   ドロワー付きシェルを描画せず、画面全体をログイン画面に差し替える
2. While セッション状態がログイン確立済み（LoggedIn 相当）であるあいだ, the App Shell shall
   ドロワー付きシェル（Requirement 1 / 2 の構造）を描画する
3. When セッション状態が LoggedOut から LoggedIn へ変化したとき, the App Shell shall
   ログイン画面を取り下げ、`timeline` ルートを初期表示としてシェルを再構築する
4. When セッション状態が LoggedIn から LoggedOut へ変化したとき, the App Shell shall
   現在表示中のルートとドロワー状態を取り下げ、ログイン画面に差し替える
5. The App Shell shall ログイン状態の観測対象を後続 Issue（#24 系）の SessionState 実装に
   差し替え可能な形で抽象化する（本 Issue では mockMode 連動などの暫定信号で表現してよい）

### Requirement 4: v1 スコープ境界の明示

**Objective:** As a プロダクト責任者, I want v1 では未対応の導線を UI に露出させないように
したい, so that 次フェーズ機能をユーザーが誤って発見・期待することを防ぐ

#### Acceptance Criteria

1. The App Shell shall v1 ではドロワーフッタの「キーワード通知」エントリを表示しない
2. The App Shell shall v1 ではドロワーフッタの「キーワード通知」エントリへのナビゲーション
   ハンドラを定義しない
3. Where 後続フェーズで「キーワード通知」が解禁される場合, the App Shell shall ドロワー
   フッタの該当エントリ表示／非表示をひとつのスイッチで切り替えられるよう責務を局所化する

## Non-Functional Requirements

### NFR 1: 応答性とアニメーション

1. When ユーザーがドロワー開閉操作を行ったとき, the App Shell shall 操作開始から 300ms
   以内にドロワーの開閉アニメーションを開始する
2. When ユーザーがドロワー項目を選択したとき, the App Shell shall ナビゲーション完了と
   ドロワー閉動作を 500ms 以内に完了させる

### NFR 2: 観測可能性とテスト容易性

1. The App Shell shall 現在のルート名・ドロワー開閉状態・セッション状態（LoggedOut /
   LoggedIn 相当）を UI テストから観測可能な形で公開する
2. The App Shell shall ログイン状態のソースを差し替え可能な依存として受け取り、テストで
   LoggedOut / LoggedIn の双方を強制できるようにする

### NFR 3: 互換性

1. The App Shell shall 既存 Issue #1 で導入されたシェル骨格（shell パッケージ配下の
   AppShell / Navigation / DrawerContent）を本 Issue の正式化版で置き換え、過渡的な
   mockMode 専用分岐を残置しない

## Out of Scope

- ドロワー内のフィード一覧（未読バッジ・状態アイコン・設定ボタン）の描画。詳細仕様と
  実装は #30 で扱う
- 上部アプリバー右側の検索・テーマ切替アクションの実装。詳細仕様と実装は #31 で扱う
- 記事詳細・フィード登録・購読設定・アカウントのボトムシート実装（呼び出し元配線を
  含む）
- SessionState の本格的な実装（トークン管理・refresh・自動ログアウト等）。本 Issue は
  「ログイン状態でない場合はログイン画面が観測される」という挙動の枠組みだけを置く
- ログイン画面そのものの UI 実装（既存の placeholder を継続使用してよい）
- ディープリンク（`feedman://auth/callback` 等）からのルート復帰挙動
- キーワード通知設定 UI（v1 スコープ外。Requirement 4 で非表示を保証）

## Open Questions

- なし（本 Issue で扱う観測条件・スコープ境界は SPEC §5.0 / §5.8 と GRAND-DESIGN §5.6 で
  確定済みのため）
