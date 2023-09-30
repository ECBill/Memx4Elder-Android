package life.memx.chat_external.view
import androidx.lifecycle.ViewModel
import androidx.databinding.ObservableField

class PerformanceMonitorViewModel :ViewModel(){
    val uploadDelay = ObservableField<String>("上传耗时：")
    val pullDelay = ObservableField<String>("拉流耗时：")
    val processingDelay = ObservableField<String>("体感处理时间：")   // from asr finish to first package received
    val asrDelay = ObservableField<String>("语音识别耗时：")
    val ttsDelay = ObservableField<String>("语音合成耗时：")

    fun setUploadDelay(delay_ms: Long) {
        uploadDelay.set("上传耗时：$delay_ms ms" )
    }

    fun setPullDelay(delay_ms: Long) {
        pullDelay.set("拉流耗时：$delay_ms ms" )
    }
    fun setProcessingDelay(delay_ms: Long) {
        if (delay_ms > 10) {
            processingDelay.set("体感处理时间：$delay_ms ms" )
        }
    }
    fun setAsrDelay(delay_ms: Long) {
        asrDelay.set("语音识别耗时：$delay_ms ms" )
    }
    fun setTtsDelay(delay_ms: Long) {
        ttsDelay.set("语音合成耗时：$delay_ms ms" )
    }

    fun reset() {
        uploadDelay.set("上传耗时：- ms" )
        pullDelay.set("拉流耗时：- ms" )
        processingDelay.set("体感处理时间：- ms" )
        asrDelay.set("语音识别耗时：- ms" )
        ttsDelay.set("语音合成耗时：- ms" )
    }

}