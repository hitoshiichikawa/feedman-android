package com.feedman.android.core.network

import java.io.IOException
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext

/**
 * Retrofit が生成した [FeedmanApi] 実装を **dynamic proxy** で包み、IOException を
 * [FeedmanException] に変換する。
 *
 * 設計判断（代替案との比較）:
 *
 * - **Interceptor のみ**: OkHttp Interceptor は `IOException` しか throw できないため、
 *   `FeedmanException`（RuntimeException）を直接呼び出し元へ運べない。
 * - **CallAdapter.Factory**: Retrofit の suspend サポートは内部 `KotlinExtensions.kt` が
 *   `await()` を直接呼んでおり、suspend 関数の戻り経路に独自 CallAdapter を綺麗に
 *   差し込むのは Retrofit 2.x の現状 API では難しい（実装可能だが hack を伴う）。
 * - **本実装（Interceptor + dynamic proxy + Continuation ラッパー）**:
 *   - Interceptor は非 2xx を [FeedmanIOException] に詰めて throw する。
 *   - dynamic proxy は呼び出しを delegate に委譲しつつ、suspend 関数の場合は最後の引数
 *     `Continuation<T>` を [ErrorConvertingContinuation] で差し替える。これにより
 *     Retrofit の `await()` が `Continuation.resumeWithException(IOException)` を呼んだ
 *     瞬間に IOException を [FeedmanException] に変換できる。
 *
 * suspend 関数は JVM 上で「最後の引数が `Continuation<T>` の通常メソッド」として
 * コンパイルされる。本 proxy はリフレクションでこの慣習を利用する。非 suspend メソッド
 * （戻り値型が `Continuation` を取らない）の場合は同期 try/catch で変換する。
 *
 * 振る舞い:
 * 1. 呼び出しを Retrofit 生成インスタンスへ委譲。
 * 2. 正常終了 → そのまま値を返す（Req 3.1）。
 * 3. [FeedmanIOException]（Interceptor 由来）→ 内包する [FeedmanException] を rethrow（Req 3.2 / 3.3 / 3.4 / 3.6）。
 * 4. その他 [IOException]（connect/read 失敗等）→ [FeedmanErrorMapper.fromIoException] で
 *    `NETWORK_ERROR` の [FeedmanException] に変換して throw（Req 3.5）。
 * 5. それ以外の throwable はそのまま透過。
 *
 * 本 proxy は OkHttp の interceptor / authenticator 拡張点とは独立した位置に存在するため、
 * 認証層が後付けされても変換層は一貫して動作する（Req 4.4）。
 */
internal object FeedmanApiProxy {

    /**
     * Retrofit 生成インスタンス [delegate] を上記方針でラップした [FeedmanApi] を返す。
     */
    fun wrap(delegate: FeedmanApi): FeedmanApi {
        val handler = ErrorUnwrappingInvocationHandler(delegate)
        val proxy = Proxy.newProxyInstance(
            FeedmanApi::class.java.classLoader,
            arrayOf(FeedmanApi::class.java),
            handler,
        )
        return proxy as FeedmanApi
    }

    /**
     * Throwable を必要なら [FeedmanException] に変換する純粋関数。
     */
    internal fun convertThrowable(cause: Throwable): Throwable {
        return when (cause) {
            is FeedmanIOException -> cause.feedmanException
            is FeedmanException -> cause
            is IOException -> FeedmanErrorMapper.fromIoException(cause)
            else -> cause
        }
    }

    /**
     * delegate 呼び出しと Continuation 差し替えを担う InvocationHandler。
     */
    private class ErrorUnwrappingInvocationHandler(
        private val delegate: FeedmanApi,
    ) : InvocationHandler {

        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
            // args は out 不変なので、自前で Array<Any?> として複製する。
            val callArgs: Array<Any?> = if (args == null) {
                emptyArray()
            } else {
                Array(args.size) { i -> args[i] }
            }
            // suspend 関数は JVM 上で「末尾引数が Continuation<T>」のメソッドとしてコンパイルされる。
            // 該当する場合は呼び出し前に Continuation をラップして差し替える。
            val lastIndex = callArgs.size - 1
            val originalContinuation = if (lastIndex >= 0) callArgs[lastIndex] as? Continuation<*> else null
            if (originalContinuation != null) {
                @Suppress("UNCHECKED_CAST")
                callArgs[lastIndex] = ErrorConvertingContinuation(
                    originalContinuation as Continuation<Any?>,
                )
            }
            return try {
                method.invoke(delegate, *callArgs)
            } catch (ite: InvocationTargetException) {
                // 同期 throw された場合（非 suspend / 引数バリデーション失敗等）はここで変換する。
                throw convertThrowable(ite.targetException ?: ite)
            }
        }
    }

    /**
     * delegate の suspend 戻り経路で resumeWithException された throwable を変換するための
     * [Continuation] デコレーター。
     */
    private class ErrorConvertingContinuation(
        private val delegate: Continuation<Any?>,
    ) : Continuation<Any?> {

        override val context: CoroutineContext
            get() = delegate.context

        override fun resumeWith(result: Result<Any?>) {
            val converted = if (result.isFailure) {
                val original = result.exceptionOrNull()!!
                val mapped = convertThrowable(original)
                Result.failure<Any?>(mapped)
            } else {
                result
            }
            delegate.resumeWith(converted)
        }
    }
}
