package life.memx.chat_external.view
import androidx.lifecycle.ViewModel
import androidx.databinding.ObservableField

class PerformanceMonitorViewModel :ViewModel(){
    val uploadDelay = ObservableField<String>("上传耗时：")
    val pullDelay = ObservableField<String>("拉流耗时：")
    val processingDelay = ObservableField<String>("体感等待时间：")   // from asr finish to first package received
    val asrDelay = ObservableField<String>("语音识别耗时：")
    val promptDelay = ObservableField<String>("prompt耗时：")
    val gptDelay = ObservableField<String>("gpt生成耗时：")
    val ttsDelay = ObservableField<String>("语音合成耗时：")

    fun setUploadDelay(delay_ms: Long) {
        uploadDelay.set("上传耗时：$delay_ms ms" )
    }

    fun setPullDelay(delay_ms: Long) {
        pullDelay.set("拉流耗时：$delay_ms ms" )
    }
    fun setProcessingDelay(delay_ms: Long) {
        if (delay_ms > 10) {
            processingDelay.set("体感等待时间(咚声之后)：$delay_ms ms" )
        }
    }

    fun setExtraStatistics(statistics: MutableMap<String, String>) {
        statistics.forEach { (k, v) ->
            when (k) {
                "asr_delay" -> asrDelay.set("语音识别耗时：$v ms" )
                "prompt_delay" -> promptDelay.set("prompt耗时：$v ms" )
                "gpt_delay" -> gptDelay.set("gpt生成耗时：$v ms" )
                "tts_delay" -> ttsDelay.set("语音合成耗时：$v ms" )
            }
        }
    }

    fun reset() {
        uploadDelay.set("上传耗时：- ms" )
        pullDelay.set("拉流耗时：- ms" )
        processingDelay.set("体感等待时间：- ms" )
        asrDelay.set("语音识别耗时：- ms" )
        ttsDelay.set("语音合成耗时：- ms" )
        promptDelay.set("prompt耗时：- ms" )
        gptDelay.set("gpt生成耗时：- ms" )
    }

}