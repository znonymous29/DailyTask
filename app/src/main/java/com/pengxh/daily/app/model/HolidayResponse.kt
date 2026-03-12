package com.pengxh.daily.app.model

import com.google.gson.annotations.SerializedName

/**
 * 节假日API响应数据模型
 * API: https://publicapi.xiaoai.me/holiday/year
 * 示例返回值:
 * {
 *   "daytype": 1, //0表示工作日、1节假日、2双休日、3调休日（需上班）
 *   "holiday": "元旦节", //节假日情况
 *   "rest": 1, //是否休息，0为不休息，1为休息
 *   "date": "2024-01-01", //日期
 *   "week": 1, //星期几，0为星期日，1为星期一，依次类推
 *   "week_desc_en": "Monday", //星期几的英文描述
 *   "week_desc_cn": "星期一" //星期几的中文描述
 * }
 */
data class HolidayResponse(
    @SerializedName("daytype")
    val dayType: Int = 0,

    @SerializedName("holiday")
    val holiday: String? = null,

    @SerializedName("rest")
    val rest: Int = 0,

    @SerializedName("date")
    val date: String = "",

    @SerializedName("week")
    val week: Int = 0,

    @SerializedName("week_desc_en")
    val weekDescEn: String? = null,

    @SerializedName("week_desc_cn")
    val weekDescCn: String? = null
) {
    /**
     * 判断当天是否为工作日（需要上班）
     * 工作日条件：daytype == 0（工作日）或 daytype == 3（调休日需上班）
     * 或者 rest == 0（不休息）
     */
    fun isWorkday(): Boolean {
        return dayType == 0 || dayType == 3 || rest == 0
    }

    /**
     * 判断当天是否为休息日（不需要上班）
     * 休息日条件：daytype == 1（节假日）或 daytype == 2（双休日）
     * 或者 rest == 1（休息）
     */
    fun isRestDay(): Boolean {
        return dayType == 1 || dayType == 2 || rest == 1
    }
}