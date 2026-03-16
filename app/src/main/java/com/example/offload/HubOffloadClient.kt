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

    suspend fun offloadToHub(
        ipAddress: String,
        deviceId: String,
        taskType: String,
        fileUri: Uri
    ): OffloadResult = withContext(Dispatchers.IO) {
        try {
            val tempFile = createTempFileFromUri(fileUri)
                ?: return@withContext OffloadResult(false, null, "Failed to read file from storage.")

            val baseUrl = if (ipAddress.startsWith("http")) ipAddress else "http://$ipAddress"
            val requestUrl = "$baseUrl/api/upload/" 

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
            connection.connectTimeout = 30000
            connection.readTimeout = 60000

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
            val responseBody = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream.bufferedReader().use { it.readText() }
            }

            tempFile.delete() // Cleanup temporary file

            if (responseCode in 200..299 && responseBody.isNotEmpty()) {
                val json = JSONObject(responseBody)
                val status = json.optString("status")
                if (status == "COMPLETED") {
                    val result = json.opt("result")?.toString() ?: "No result details"
                    return@withContext OffloadResult(true, result, "Success")
                } else {
                    return@withContext OffloadResult(false, null, "Hub Backend Error: $responseBody")
                }
            } else {
                return@withContext OffloadResult(false, null, "HTTP Error: $responseCode - $responseBody")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext OffloadResult(false, null, "Exception: ${e.message}")
        }
    }

    private fun createTempFileFromUri(uri: Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val tempFile = File(context.cacheDir, "upload_temp_${System.currentTimeMillis()}")
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
    val errorMessage: String?
)
