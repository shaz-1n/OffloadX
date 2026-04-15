package com.example.offload

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class HubOffloadClient(private val context: Context) {

    /**
     * Offloads a file to either the Hub (edge node) or the Cloud tier.
     *
     * @param executionTier  "HUB" -> /api/upload/  |  "CLOUD" -> /api/cloud/upload/
     */
    suspend fun offloadToHub(
        ipAddress: String,
        deviceId: String,
        taskType: String,
        fileUri: Uri,
        executionTier: String = "HUB",
        imageMode: String  = "GRAYSCALE",
        pdfMode: String    = "ANALYZE",
        textMode: String   = "WORD_COUNT",
        videoMode: String  = "FACE_DETECTION"
    ): OffloadResult = withContext(Dispatchers.IO) {
        try {
            val tempFile = createTempFileFromUri(fileUri)
                ?: return@withContext OffloadResult(
                    false, null,
                    "Failed to read file from storage.",
                    executionTier = executionTier
                )

            val baseUrl = if (ipAddress.startsWith("http")) ipAddress else "http://$ipAddress"

            // Route to cloud endpoint when CLOUD tier is selected
            val apiPath = if (executionTier == "CLOUD") "/api/cloud/upload/" else "/api/upload/"
            val requestUrl = "$baseUrl$apiPath"

            val boundary = "*****${UUID.randomUUID()}*****"
            val twoHyphens = "--"
            val crlf = "\r\n"

            val url = URL(requestUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.useCaches = false
            connection.doOutput = true
            connection.doInput = true
            connection.requestMethod = "POST"
            connection.setRequestProperty("Connection", "Keep-Alive")
            connection.setRequestProperty("Cache-Control", "no-cache")
            connection.setRequestProperty("Content-Type", "multipart/form-data;boundary=$boundary")
            // Cloud tier may be slower due to simulated overhead - extend timeout
            connection.connectTimeout = if (executionTier == "CLOUD") 90_000 else 60_000
            connection.readTimeout   = if (executionTier == "CLOUD") 180_000 else 120_000

            val request = DataOutputStream(connection.outputStream)

            // Add device_id parameter
            request.writeBytes(twoHyphens + boundary + crlf)
            request.writeBytes("Content-Disposition: form-data; name=\"device_id\"$crlf")
            request.writeBytes(crlf)
            request.writeBytes(deviceId + crlf)

            // Add task_type parameter
            request.writeBytes(twoHyphens + boundary + crlf)
            request.writeBytes("Content-Disposition: form-data; name=\"task_type\"$crlf")
            request.writeBytes(crlf)
            request.writeBytes(taskType + crlf)

            // Add image_mode parameter (tells the server which image pipeline to run)
            request.writeBytes(twoHyphens + boundary + crlf)
            request.writeBytes("Content-Disposition: form-data; name=\"image_mode\"$crlf")
            request.writeBytes(crlf)
            request.writeBytes(imageMode + crlf)

            // Add pdf_mode parameter
            request.writeBytes(twoHyphens + boundary + crlf)
            request.writeBytes("Content-Disposition: form-data; name=\"pdf_mode\"$crlf")
            request.writeBytes(crlf)
            request.writeBytes(pdfMode + crlf)

            // Add text_mode parameter
            request.writeBytes(twoHyphens + boundary + crlf)
            request.writeBytes("Content-Disposition: form-data; name=\"text_mode\"$crlf")
            request.writeBytes(crlf)
            request.writeBytes(textMode + crlf)

            // Add video_mode parameter
            request.writeBytes(twoHyphens + boundary + crlf)
            request.writeBytes("Content-Disposition: form-data; name=\"video_mode\"$crlf")
            request.writeBytes(crlf)
            request.writeBytes(videoMode + crlf)

            // Add file parameter
            request.writeBytes(twoHyphens + boundary + crlf)
            request.writeBytes("Content-Disposition: form-data; name=\"file\";filename=\"${tempFile.name}\"$crlf")
            request.writeBytes("Content-Type: application/octet-stream$crlf")
            request.writeBytes(crlf)

            val fileInputStream = FileInputStream(tempFile)
            val buffer = ByteArray(4096)
            var bytesRead: Int
            while (fileInputStream.read(buffer).also { bytesRead = it } != -1) {
                request.write(buffer, 0, bytesRead)
            }
            fileInputStream.close()

            request.writeBytes(crlf)
            request.writeBytes(twoHyphens + boundary + twoHyphens + crlf)
            request.flush()
            request.close()

            val responseCode = connection.responseCode
            val responseBody = try {
                if (responseCode in 200..299) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error body"
                }
            } catch (e: Exception) {
                "Read error: ${e.message}"
            }

            tempFile.delete() // Cleanup temporary file

            if (responseCode in 200..299 && responseBody.isNotEmpty()) {
                val json = JSONObject(responseBody)
                val st = json.optString("status")
                val serverMs = json.optDouble("processing_time_ms", 0.0).toLong()
                val tier = json.optString("execution_tier", executionTier)

                if (st == "COMPLETED") {
                    val resultObj = json.optJSONObject("result")
                    val rawUrl = resultObj?.optString("processed_url") ?: ""
                    // Avoid double-prefixing if the server already returned an absolute URL
                    val finalUrl = when {
                        rawUrl.isEmpty()          -> ""
                        rawUrl.startsWith("http") -> rawUrl
                        else                      -> baseUrl + rawUrl
                    }
                    // Accept "partial" (fallback image) as a displayable result too
                    val resultStatus = resultObj?.optString("status") ?: "success"
                    val modeUsed = resultObj?.optString("mode") ?: ""
                    val successMsg = if (resultStatus == "partial") "Partial: $modeUsed" else "Success"
                    return@withContext OffloadResult(true, finalUrl, successMsg, serverMs, tier)
                } else {
                    return@withContext OffloadResult(false, null, "Backend Error: $responseBody", serverMs, tier)
                }
            } else {
                return@withContext OffloadResult(
                    false, null,
                    "HTTP Error: $responseCode — $responseBody",
                    executionTier = executionTier
                )
            }
        } catch (e: java.net.SocketTimeoutException) {
            return@withContext OffloadResult(
                false, null,
                "Hub timed out. Check server.",
                executionTier = executionTier
            )
        } catch (e: java.net.ConnectException) {
            return@withContext OffloadResult(
                false, null,
                "Cannot connect to $ipAddress.",
                executionTier = executionTier
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext OffloadResult(false, null, "Exception: ${e.message}", executionTier = executionTier)
        }
    }

    private fun createTempFileFromUri(uri: Uri): File? {
        return try {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri) ?: ""

            // Derive the correct extension from MIME type so the server can identify the file
            val ext = when {
                mimeType.contains("video")                      -> ".mp4"
                mimeType.contains("pdf")                        -> ".pdf"
                mimeType.contains("text/plain")                 -> ".txt"
                mimeType.contains("text/csv")                   -> ".csv"
                mimeType.contains("application/json")           -> ".json"
                mimeType.contains("application/xml")
                    || mimeType.contains("text/xml")            -> ".xml"
                mimeType.contains("msword")
                    || mimeType.contains("wordprocessingml")    -> ".docx"
                mimeType.contains("spreadsheetml")
                    || mimeType.contains("ms-excel")            -> ".xlsx"
                mimeType.contains("png")                        -> ".png"
                mimeType.contains("gif")                        -> ".gif"
                mimeType.contains("webp")                       -> ".webp"
                else                                            -> ".jpg"   // default / unknown
            }

            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val tempFile = File(context.cacheDir, "upload_temp_${System.currentTimeMillis()}$ext")
            val outputStream = FileOutputStream(tempFile)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()
            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

data class OffloadResult(
    val success: Boolean,
    val resultMsg: String?,
    val errorMessage: String?,
    val serverProcessingTimeMs: Long = 0L,   // pure compute time on hub (ms)
    val executionTier: String = "HUB"        // "HUB" or "CLOUD"
)
