package dev.whysoezzy.uikit.tokens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Central icon spec (R-063). Единый источник иконок приложения.
 * Все call-site'ы ссылаются сюда, а не на androidx.compose.material.icons.* напрямую.
 * Замена источника (Material Symbols XML / другая либа) — правка только этого файла.
 *
 * Под капотом пока material-icons-core (RX-020, Сессия 11). Набор депрекейтнут upstream;
 * deprecated-иконки уже заменены на AutoMirrored-версии внутри спеки.
 */
object AppIcons {
    val Person: ImageVector get() = Icons.Filled.Person
    val Check: ImageVector get() = Icons.Filled.Check
    val Add: ImageVector get() = Icons.Filled.Add
    val Share: ImageVector get() = Icons.Filled.Share
    val Close: ImageVector get() = Icons.Filled.Close
    val Edit: ImageVector get() = Icons.Filled.Edit
    val Info: ImageVector get() = Icons.Filled.Info
    val CheckCircle: ImageVector get() = Icons.Filled.CheckCircle

    val Search: ImageVector get() = Icons.Filled.Search
    val SearchOutlined: ImageVector get() = Icons.Outlined.Search
    val Clear: ImageVector get() = Icons.Filled.Clear
    val ClearOutlined: ImageVector get() = Icons.Outlined.Clear

    // AutoMirrored — корректное зеркалирование в RTL (рекоменд. Google взамен deprecated)
    val Back: ImageVector get() = Icons.AutoMirrored.Filled.KeyboardArrowLeft
    val ArrowBack: ImageVector get() = Icons.AutoMirrored.Filled.ArrowBack
}
