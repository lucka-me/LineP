package labs.zero_one.patroute

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.drawable.Drawable

/**
 * 对话框工具，提供快速显示简单对话框的方法
 *
 * ## 方法列表
 * - [showDialog]
 * - [showSimpleAlert]
 *
 * @author lucka-me
 * @since 1.5.0
 */
class DialogKit {

    companion object {

        /**
         * 显示对话框
         *
         * @param [context] 环境
         * @param [titleId] 标题资源 ID
         * @param [message] 消息
         * @param [positiveButtonTextId] PositiveButton 文字资源 ID，默认为「确认」
         * @param [positiveButtonListener] PositiveButton 点击监听器，可选
         * @param [negativeButtonTextId] NegativeButton 文字资源 ID，可选
         * @param [negativeButtonListener] NegativeButton 点击监听器，可选
         * @param [icon] 图标，可选
         * @param [cancelable] 是否可快速取消，可选
         *
         * @see <a href="https://www.jianshu.com/p/6bd7dd1cd491">使用着色器修改 Drawable 颜色</a>
         *
         * @author lucka-me
         * @since 1.5.0
         */
        fun showDialog(
            context: Context, titleId: Int, message: String?,
            positiveButtonTextId: Int = R.string.confirm,
            positiveButtonListener: ((DialogInterface, Int) -> (Unit))? = null,
            negativeButtonTextId: Int? = null,
            negativeButtonListener: ((DialogInterface, Int) -> (Unit))? = null,
            icon: Drawable? = null,
            cancelable: Boolean? = null
        ) {

            val builder = AlertDialog.Builder(context)
                .setTitle(titleId)
                .setIcon(icon)
                .setMessage(message)
                .setPositiveButton(positiveButtonTextId, positiveButtonListener)

            if (negativeButtonTextId != null)
                builder.setNegativeButton(negativeButtonTextId, negativeButtonListener)
            if (cancelable != null) builder.setCancelable(cancelable)

            builder.show()

        }

        /**
         * 显示对话框
         *
         * @param [context] 环境
         * @param [titleId] 标题资源 ID
         * @param [messageId] 消息
         * @param [positiveButtonTextId] PositiveButton 文字资源 ID，默认为「确认」
         * @param [positiveButtonListener] PositiveButton 点击监听器，可选
         * @param [negativeButtonTextId] NegativeButton 文字资源 ID，可选
         * @param [negativeButtonListener] NegativeButton 点击监听器，可选
         * @param [icon] 图标，可选
         * @param [cancelable] 是否可快速取消，可选
         *
         * @author lucka-me
         * @since 1.5.0
         */
        fun showDialog(
            context: Context, titleId: Int, messageId: Int,
            positiveButtonTextId: Int = R.string.confirm,
            positiveButtonListener: ((DialogInterface, Int) -> (Unit))? = null,
            negativeButtonTextId: Int? = null,
            negativeButtonListener: ((DialogInterface, Int) -> (Unit))? = null,
            icon: Drawable? = null,
            cancelable: Boolean? = null
        ) {

            showDialog(
                context, titleId, context.getString(messageId), positiveButtonTextId,
                positiveButtonListener,
                negativeButtonTextId, negativeButtonListener,
                icon,
                cancelable)

        }

        /**
         * 显示简单警告对话框
         *
         * @param [context] 环境
         * @param [message] 消息
         *
         * @author lucka-me
         * @since 1.5.0
         */
        fun showSimpleAlert(context: Context, message: String?) {
            showDialog(context, R.string.alert_title, message, cancelable = false)
        }

        /**
         * 显示简单警告对话框
         *
         * @param [context] 环境
         * @param [messageId] 消息资源 ID
         *
         * @author lucka-me
         * @since 1.5.0
         */
        fun showSimpleAlert(context: Context, messageId: Int) {
            showSimpleAlert(context, context.getString(messageId))
        }
    }
}