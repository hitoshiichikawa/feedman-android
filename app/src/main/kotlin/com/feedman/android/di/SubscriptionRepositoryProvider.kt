package com.feedman.android.di

import com.feedman.android.core.data.SubscriptionRepository
import com.feedman.android.core.data.SubscriptionRepositoryImpl
import com.feedman.android.core.data.fake.FakeSubscriptionRepository

/**
 * `AppConfig.mockMode` に応じて [SubscriptionRepository] 実装を選択する純粋関数
 * （Issue #39 / Req 3.1, 3.3, NFR 2.3）。
 *
 * Hilt module（[RepositoryModule]）側で `@Provides` から本関数を呼び出すことで、
 * 切替ロジックを独立に単体テスト可能にする（NFR 2.3: 依存バインドの単一箇所の差し替え
 * で再現できる構成）。
 *
 * - `mockMode = true`  → [FakeSubscriptionRepository]（Req 3.1, 3.2）
 * - `mockMode = false` → [SubscriptionRepositoryImpl]（Req 3.3）
 *
 * 戻り値は [SubscriptionRepository] 抽象として返すため、UI / ViewModel 側は両系統で
 * 同じ公開インターフェースを使う（Req 3.4 / NFR 1.2）。
 */
internal fun selectSubscriptionRepository(
    mockMode: Boolean,
    fake: FakeSubscriptionRepository,
    real: SubscriptionRepositoryImpl,
): SubscriptionRepository = if (mockMode) fake else real
