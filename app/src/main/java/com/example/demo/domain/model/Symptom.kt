package com.example.demo.domain.model

/**
 * 症状标签字典。
 *
 * ## 为什么数据库存逗号分隔字符串而代码用 enum
 * 与 [PomodoroLinkType] 同理：存字符串省一个 TypeConverter，且症状是「多选集合」，
 * 关系型数据库存集合要么建关联表（demo 规模不值得）、要么存分隔串。
 * 用 enum 让调用方拿到类型安全的值，UI 的 [label] 直接可显示，
 * [code] 是两者之间的翻译层，保证落库内容稳定（改 label 不影响历史数据）。
 *
 * ## 为什么放在 domain 包
 * 纯 Kotlin、零 Android 依赖；Repository、ViewModel、JVM 单测都要引用。
 */
enum class Symptom(val code: String, val label: String) {
    CRAMPS("cramps", "腹痛"),
    HEADACHE("headache", "头痛"),
    BACKACHE("backache", "腰酸"),
    FATIGUE("fatigue", "疲惫"),
    MOOD_SWING("mood_swing", "情绪波动"),
    IRRITABILITY("irritability", "易怒"),
    BLOATING("bloating", "腹胀"),
    BREAST_TENDERNESS("breast_tenderness", "胸胀"),
    ACNE("acne", "痘痘"),
    INSOMNIA("insomnia", "失眠"),
    NAUSEA("nausea", "恶心"),
    CRAVINGS("cravings", "食欲变化");

    companion object {
        /** 容错解析：遇到字典里没有的旧 code 直接跳过，不让历史脏数据炸掉 UI */
        fun fromCode(code: String): Symptom? = entries.firstOrNull { it.code == code }

        /** "cramps,headache" -> [CRAMPS, HEADACHE] */
        fun parseCodes(csv: String): Set<Symptom> =
            csv.split(',').mapNotNull { fromCode(it.trim()) }.toSet()

        /** [CRAMPS, HEADACHE] -> "cramps,headache" */
        fun toCodes(symptoms: Set<Symptom>): String =
            symptoms.joinToString(",") { it.code }
    }
}
