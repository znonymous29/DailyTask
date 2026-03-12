package com.pengxh.daily.app.utils

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pengxh.daily.app.model.HolidayResponse
import com.pengxh.kt.lite.utils.SaveKeyValues
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import kotlin.collections.HashMap

/**
 * 节假日管理器
 * 负责获取和缓存节假日数据，判断指定日期是否为工作日
 */
object HolidayManager {
    private const val TAG = "HolidayManager"
    private const val API_URL = "https://publicapi.xiaoai.me/holiday/year"
    private const val CACHE_KEY_PREFIX = "holiday_data_cache_"
    private const val CACHE_TIMESTAMP_KEY_PREFIX = "holiday_cache_timestamp_"

    private val executor = Executors.newSingleThreadExecutor()
    private val gson = Gson()

    // 内存缓存：日期 -> HolidayResponse
    private val holidayCache = HashMap<String, HolidayResponse>()

    /**
     * 初始化节假日数据
     * 如果缓存有效则使用缓存，否则从网络获取
     */
    fun initialize(context: Context, forceRefresh: Boolean = false) {
        executor.execute {
            try {
                // 检查是否需要强制刷新或缓存无效
                val needRefresh = forceRefresh || !isCacheValid() || !isCacheYearValid()

                if (!needRefresh) {
                    loadFromCache()
                    Log.d(TAG, "使用缓存的节假日数据")
                    return@execute
                }

                Log.d(TAG, "开始获取节假日数据")
                val holidayData = fetchHolidayDataFromNetwork()
                if (holidayData.isNotEmpty()) {
                    holidayCache.clear()
                    holidayData.forEach { response ->
                        holidayCache[response.date] = response
                    }
                    saveToCache(holidayData)
                    Log.d(TAG, "节假日数据获取成功，共${holidayData.size}条记录")
                } else {
                    Log.e(TAG, "获取的节假日数据为空")
                }
            } catch (e: Exception) {
                Log.e(TAG, "初始化节假日数据失败: ${e.message}", e)
                // 如果网络获取失败，尝试使用缓存
                if (holidayCache.isEmpty()) {
                    loadFromCache()
                }
            }
        }
    }

    /**
     * 判断指定日期是否为工作日（需要上班）
     * @param date 日期字符串，格式：yyyy-MM-dd
     * @return true: 工作日，false: 休息日或未知
     */
    fun isWorkday(date: String = getTodayDate()): Boolean {
        // 先从缓存查找
        val holidayResponse = holidayCache[date]
        return if (holidayResponse != null) {
            holidayResponse.isWorkday()
        } else {
            // 如果缓存中没有，根据星期判断（周末休息，周一到周五工作）
            // 这是一个后备方案，当没有节假日数据时使用
            isWeekday(date)
        }
    }

    /**
     * 判断指定日期是否为休息日（不需要上班）
     */
    fun isRestDay(date: String = getTodayDate()): Boolean {
        return !isWorkday(date)
    }

    /**
     * 判断今天是否为工作日
     */
    fun isTodayWorkday(): Boolean {
        return isWorkday(getTodayDate())
    }

    /**
     * 判断今天是否为休息日
     */
    fun isTodayRestDay(): Boolean {
        return isRestDay(getTodayDate())
    }

    /**
     * 强制刷新节假日数据
     */
    fun refresh(context: Context) {
        initialize(context, true)
    }

    /**
     * 清除缓存
     * 清除当前年份的缓存
     */
    fun clearCache(context: Context) {
        holidayCache.clear()
        SaveKeyValues.putValue(getCacheKey(), "")
        SaveKeyValues.putValue(getCacheTimestampKey(), 0L)
        Log.d(TAG, "已清除当前年份（${getCurrentYear()}）的节假日缓存")
    }

    // ==================== 私有方法 ====================

    /**
     * 获取今天的日期字符串
     */
    private fun getTodayDate(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
        return dateFormat.format(Date())
    }

    /**
     * 获取当前年份
     */
    private fun getCurrentYear(): Int {
        val calendar = Calendar.getInstance()
        return calendar.get(Calendar.YEAR)
    }

    /**
     * 获取当前年份的缓存键
     */
    private fun getCacheKey(year: Int = getCurrentYear()): String {
        return "${CACHE_KEY_PREFIX}$year"
    }

    /**
     * 获取当前年份的缓存时间戳键
     */
    private fun getCacheTimestampKey(year: Int = getCurrentYear()): String {
        return "${CACHE_TIMESTAMP_KEY_PREFIX}$year"
    }

    /**
     * 从日期字符串中提取年份
     * @param date 日期字符串，格式：yyyy-MM-dd
     */
    private fun extractYearFromDate(date: String): Int? {
        return try {
            date.substring(0, 4).toInt()
        } catch (e: Exception) {
            Log.e(TAG, "从日期提取年份失败: $date", e)
            null
        }
    }

    /**
     * 检查缓存数据的年份是否有效
     * 缓存数据应包含当前年份的日期
     */
    private fun isCacheYearValid(): Boolean {
        if (holidayCache.isNotEmpty()) {
            // 检查内存缓存中是否有当前年份的日期
            val currentYear = getCurrentYear()
            return holidayCache.keys.any { date ->
                extractYearFromDate(date) == currentYear
            }
        }

        // 检查存储的缓存数据
        val cachedJson = SaveKeyValues.getValue(getCacheKey(), "") as String
        if (cachedJson.isEmpty()) {
            return false
        }

        return try {
            val type = object : TypeToken<List<HolidayResponse>>() {}.type
            val holidayData = gson.fromJson<List<HolidayResponse>>(cachedJson, type)
            val currentYear = getCurrentYear()
            holidayData?.any { response ->
                extractYearFromDate(response.date) == currentYear
            } == true
        } catch (e: Exception) {
            Log.e(TAG, "检查缓存年份有效性时失败", e)
            false
        }
    }

    /**
     * 根据日期判断是否为工作日（仅基于星期，不考虑节假日）
     */
    private fun isWeekday(dateString: String): Boolean {
        return try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
            val date = dateFormat.parse(dateString)
            val calendar = Calendar.getInstance().apply {
                time = date
            }
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            // Calendar中：SUNDAY=1, MONDAY=2, ..., SATURDAY=7
            dayOfWeek != Calendar.SUNDAY && dayOfWeek != Calendar.SATURDAY
        } catch (e: Exception) {
            Log.e(TAG, "解析日期失败: $dateString", e)
            true // 默认按工作日处理
        }
    }

    /**
     * 从网络获取节假日数据
     */
    private fun fetchHolidayDataFromNetwork(): List<HolidayResponse> {
        val url = URL(API_URL)
        val connection = url.openConnection() as HttpURLConnection
        connection.apply {
            requestMethod = "GET"
            connectTimeout = 10000 // 10秒
            readTimeout = 10000 // 10秒
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "DailyTask/2.2.5.2")
        }

        return try {
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()
                inputStream.close()

                // 解析JSON响应
                val type = object : TypeToken<List<HolidayResponse>>() {}.type
                gson.fromJson<List<HolidayResponse>>(response.toString(), type) ?: emptyList()
            } else {
                Log.e(TAG, "网络请求失败，状态码: $responseCode")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取节假日数据失败", e)
            throw e
        } finally {
            connection.disconnect()
        }
    }

    /**
     * 检查缓存是否有效
     * 检查缓存是否包含当前日期的数据
     */
    private fun isCacheValid(): Boolean {
        // 如果内存缓存不为空且包含今天的数据，则认为缓存有效
        if (holidayCache.isNotEmpty() && holidayCache.containsKey(getTodayDate())) {
            return true
        }

        // 检查是否有当前年份的缓存数据
        val cachedJson = SaveKeyValues.getValue(getCacheKey(), "") as String
        if (cachedJson.isEmpty()) {
            return false
        }

        // 尝试解析缓存数据，检查是否包含今天的数据
        return try {
            val type = object : TypeToken<List<HolidayResponse>>() {}.type
            val holidayData = gson.fromJson<List<HolidayResponse>>(cachedJson, type)
            holidayData?.any { it.date == getTodayDate() } == true
        } catch (e: Exception) {
            Log.e(TAG, "检查缓存有效性时解析失败", e)
            false
        }
    }

    /**
     * 从SharedPreferences加载缓存数据
     * 加载当前年份的缓存数据
     */
    private fun loadFromCache() {
        val cachedJson = SaveKeyValues.getValue(getCacheKey(), "") as String
        if (cachedJson.isNotEmpty()) {
            try {
                val type = object : TypeToken<List<HolidayResponse>>() {}.type
                val holidayData = gson.fromJson<List<HolidayResponse>>(cachedJson, type)
                holidayCache.clear()
                holidayData?.forEach { response ->
                    holidayCache[response.date] = response
                }
                Log.d(TAG, "从缓存加载节假日数据成功，共${holidayCache.size}条记录，年份：${getCurrentYear()}")
            } catch (e: Exception) {
                Log.e(TAG, "解析缓存数据失败", e)
                holidayCache.clear()
            }
        }
    }

    /**
     * 将数据保存到SharedPreferences缓存
     * 保存当前年份的数据
     */
    private fun saveToCache(holidayData: List<HolidayResponse>) {
        if (holidayData.isEmpty()) {
            Log.w(TAG, "节假日数据为空，不进行缓存")
            return
        }

        try {
            val json = gson.toJson(holidayData)
            SaveKeyValues.putValue(getCacheKey(), json)
            SaveKeyValues.putValue(getCacheTimestampKey(), System.currentTimeMillis())

            // 从数据中提取年份用于日志记录
            val yearFromData = holidayData.firstOrNull()?.date?.substring(0, 4) ?: "未知"
            Log.d(TAG, "节假日数据已缓存，年份：$yearFromData，共${holidayData.size}条记录")
        } catch (e: Exception) {
            Log.e(TAG, "保存缓存失败", e)
        }
    }
}