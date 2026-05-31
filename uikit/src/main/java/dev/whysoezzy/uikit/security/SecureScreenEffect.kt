package dev.whysoezzy.uikit.security

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import java.util.WeakHashMap

/**
 * Помечает текущий экран как чувствительный: пока он в композиции, на окне Activity
 * стоит FLAG_SECURE — система блокирует скриншоты, запись экрана, кастинг и показывает
 * пустое превью в Recents. Флаг снимается, когда чувствительных экранов на окне не осталось.
 *
 * Безопасно при наложении экранов (например, ProfileDetails → ProfileEdit): используется
 * ref-count по окну, поэтому dispose уходящего экрана не оголяет окно для входящего.
 *
 * В @Preview и вне Activity-контекста — no-op (Activity не найдена).
 */
@Composable
fun SecureScreenEffect() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        if (window == null) {
            onDispose { }
        } else {
            SecureFlagRefCounter.acquire(window)
            onDispose { SecureFlagRefCounter.release(window) }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

/**
 * Ref-count FLAG_SECURE по окну. Обращения только с main-потока (Compose effects) —
 * синхронизация не требуется. WeakHashMap не удерживает Window/Activity.
 */
private object SecureFlagRefCounter {
    private val counts = WeakHashMap<Window, Int>()

    fun acquire(window: Window) {
        val next = (counts[window] ?: 0) + 1
        counts[window] = next
        if (next == 1) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    fun release(window: Window) {
        val next = ((counts[window] ?: 0) - 1).coerceAtLeast(0)
        if (next == 0) {
            counts.remove(window)
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            counts[window] = next
        }
    }
}
