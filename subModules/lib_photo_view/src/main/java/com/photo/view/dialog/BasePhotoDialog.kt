package com.photo.view.dialog

import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Color
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup.LayoutParams
import android.view.ViewTreeObserver
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import androidx.activity.ComponentDialog
import androidx.activity.OnBackPressedCallback
import androidx.core.graphics.ColorUtils
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.gyf.immersionbar.BarHide
import com.gyf.immersionbar.ImmersionBar
import com.photo.view.PhotoView
import com.photo.view.R
import com.photo.view.listener.OnViewExitListener
import kotlin.math.absoluteValue

class BasePhotoDialog(
    val context: Activity,
    private val normalRectF: RectF,
    private val layoutId : Int = R.layout.dialog_base_photo,
) : ComponentDialog(context) {

    private lateinit var rootView : View
    protected lateinit var photoView: PhotoView
    // 背景渐变：只改变 ColorDrawable 的 Alpha
    private var startProgress = 0f // 0% 透明度
    private var endProgress = 1f // 100% 透明度 (黑色)
    //只有当显示动画执行完成之后才可以退出
    private var dialogShowing = true
    //沉浸式布局
    private var showBar = false

    //动画进度
    private var totalProgress = 0f
    //最小缩比例
    private var minScale = 0.6f
    //最小透明度
    private var minAlpha = 0.3f

    /**
     * 亮色
     */
    private val lightColor = Color.WHITE

    /**
     * 暗色
     */
    private val darkColor = Color.BLACK
    /**
     * 当前颜色
     */
    private var currentColor = darkColor

    companion object{
        fun show(activity: Activity,rectF: RectF,layoutId : Int = R.layout.dialog_base_photo) {
            val dialog = BasePhotoDialog(activity,rectF,layoutId)
            dialog.show()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        rootView = LayoutInflater.from(context).inflate(layoutId, null, false)
        photoView = rootView.findViewById(R.id.photoView) ?: throw IllegalStateException("布局中必须包含 id 为 photoView 的控件")
        setContentView(rootView)

        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.setLayout(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        window?.setDimAmount(0f)

        initView()
        initListener()

    }

    private fun initView() {
        changeStyle()
        //当布局加载完成后才开始执行动画
        photoView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                photoView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                animateShow()
            }
        })
        photoView.setImageResource(R.drawable.img_home_top)
        photoView.bindExitParent(rootView.rootView)
    }

    private fun initListener() {
        //监听返回键，只有显示动画执行完成之后才可以退出
        onBackPressedDispatcher.addCallback(this,object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!dialogShowing) {
                    animateDismiss(false)
                }
            }
        })

        //监听拖拽退出
        photoView.setOnViewExitListener(object : OnViewExitListener {
            override fun onDrag(totalTranslateX: Float,totalTranslateY: Float,progress: Float,img : ImageView):Boolean {
                totalProgress = totalTranslateY.absoluteValue / rootView.height
                img.apply {
                    scaleX = (1 - totalProgress).coerceIn(minScale,1f)
                    scaleY = (1 - totalProgress).coerceIn(minScale,1f)
                    translationX = totalTranslateX
                    translationY = totalTranslateY
                }
                val alpha = (1 - totalProgress).coerceIn(minAlpha,1f)
                val color= ColorUtils.setAlphaComponent(currentColor,(alpha * 255).toInt())
                Log.d("myLogD", "onDrag:$totalProgress: $progress $alpha : $color")
                rootView.setBackgroundColor(color)
                return false
            }
            override fun onExit() {
                animateDismiss(true)
            }
            override fun onRestore(progress : Float) : Boolean{
                animateReset(totalProgress)
                return false
            }
        })

        //点击图片改变样式
        photoView.setOnPhotoTapListener { _, _, _ ->
            changeStyle()
        }
        //点击图片外部退出
        photoView.setOnOutsidePhotoTapListener{ animateDismiss(false) }
    }

    private fun changeStyle(){
        Log.d("myLogD", "changeStyle: ")
        val immersionBar = ImmersionBar.with(context,this).reset()

        currentColor = if (showBar) lightColor else darkColor
        rootView.setBackgroundColor(currentColor)


        val statusBarColor = if (!showBar) {
            immersionBar.hideBar(BarHide.FLAG_HIDE_BAR)
            R.color.white
        } else {
            R.color.black
        }
        immersionBar.transparentBar()
            .statusBarDarkFont(showBar)
            .navigationBarDarkIcon(showBar)
            .statusBarColor(statusBarColor)
            .navigationBarColor(statusBarColor)
            .init()
        showBar = !showBar
    }

    private fun animateShow(){
        Log.d("myLogD", "animateShow: ")
        val decor = window?.decorView ?: return
        val dw = decor.width.toFloat()
        val dh = decor.height.toFloat()

        val startRect = normalRectF
        val scaleX = startRect.width() / dw
        val scaleY = startRect.height() / dh
        val transX = startRect.centerX() - dw / 2f
        val transY = startRect.centerY() - dh / 2f

        photoView.apply {
            this.scaleX = scaleX
            this.scaleY = scaleY
            this.translationX = transX
            this.translationY = transY
        }

        runPhotoViewAnim(
            scaleX = 1f,
            scaleY = 1f,
            transX = 0f,
            transY = 0f,
            bgFrom = startProgress,
            bgTo = endProgress,
            duration = 350L,
            interpolator = FastOutSlowInInterpolator()
        ){
            dialogShowing = false
        }
    }

    /**
     * 拖拽后达到阈值，执行关闭动画。
     */
    private fun animateDismiss(isDrag : Boolean) {
        Log.d("myLogD", "animateDismiss: ")
        val decor = window?.decorView ?: return
        val dw = decor.width.toFloat()
        val dh = decor.height.toFloat()

        val endRect = normalRectF
        val scaleX = endRect.width() / dw
        val scaleY = endRect.height() / dh
        val transX = endRect.centerX() - dw / 2f
        val transY = endRect.centerY() - dh / 2f

        runPhotoViewAnim(
            scaleX = scaleX,
            scaleY = scaleY,
            transX = transX,
            transY = transY,
            bgFrom = if (isDrag) minAlpha else endProgress,
            bgTo = startProgress,
            duration = 250L,
            interpolator = AccelerateDecelerateInterpolator()
        ) {

            dismiss()
        }
    }
    /**
     * 平滑复原到初始状态
     */
    private fun animateReset(progress : Float) {
        runPhotoViewAnim(
            scaleX = 1f,
            scaleY = 1f,
            transX = 0f,
            transY = 0f,
            bgFrom = 1 - progress, // 可选：中间状态透明度恢复
            bgTo = endProgress,
            duration = 200L,
            interpolator = DecelerateInterpolator()
        )
    }

    private fun runPhotoViewAnim(
        scaleX: Float,
        scaleY: Float,
        transX: Float,
        transY: Float,
        bgFrom: Float,
        bgTo: Float,
        duration: Long,
        interpolator: TimeInterpolator,
        endAction: () -> Unit = {}
    ) {
        // 背景渐变动画
        ValueAnimator.ofFloat(bgFrom, bgTo).apply {
            this.duration = duration
            this.interpolator = interpolator
            addUpdateListener {
                val alpha = it.animatedValue as Float
                val color = ColorUtils.setAlphaComponent(currentColor,(alpha * 255).toInt())
                Log.d("myLogD", "onDrag: $alpha : $color")
                rootView.setBackgroundColor(color)
            }
            start()
        }

        // 图片缩放/位移动画
        photoView.animate()
            .scaleX(scaleX)
            .scaleY(scaleY)
            .translationX(transX)
            .translationY(transY)
            .setInterpolator(interpolator)
            .setDuration(duration)
            .withStartAction { dialogShowing = true }
            .withEndAction {
                endAction()
            }
            .start()
    }


    override fun dismiss() {
        destroyDialog()
        super.dismiss()
    }
    open fun destroyDialog() {
        ImmersionBar.destroy(context, this)
    }
}