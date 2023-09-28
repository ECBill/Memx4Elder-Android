package life.memx.chat_external.utils

import java.util.HashMap

class TimerUtil {
    private val timers = HashMap<Int, Long>() // 用于存储计时器的起始时间

    // 启动计时器
    fun startTimer(id: Int) {
        val startTime = System.currentTimeMillis()
        timers[id] = startTime // 存储起始时间
    }

    // 停止计时器并返回所用时间
    fun stopTimer(id: Int): Long {
        val startTime = timers[id]
        if (startTime != null) {
            val endTime = System.currentTimeMillis()
            val elapsedTime = endTime - startTime
            timers.remove(id) // 移除计时器记录
            return elapsedTime
        } else {
            return -1
        }
    }
}

fun main() {
    val timerUtil = TimerUtil()

    // 启动计时器
    timerUtil.startTimer(1)

    // 模拟一些操作
    Thread.sleep(2000) // 休眠2秒钟

    // 停止计时器并获取所用时间
    val elapsedTime = timerUtil.stopTimer(1)

    println("计时器1所用时间: $elapsedTime 毫秒")
}
