package com.example.systemservice

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object FileEncryptionUtil {
    private const val TAG = "FileEncryptionUtil"
    private const val ENCRYPTION_ALGORITHM = "AES/CBC/PKCS5Padding"
    private const val SECRET_KEY = "0123456789abcdef" // Replace with a secure key in production
    private const val MIN_FREE_SPACE_MB = 500 // Minimum free space to maintain (in MB)
    
    /**
     * Encrypt a file using AES-256 encryption.
     *
     * @param inputFile The file to encrypt
     * @param outputFile The encrypted output file
     * @return True if encryption was successful, false otherwise
     */
    fun encryptFile(inputFile: File, outputFile: File): Boolean {
        try {
            // Generate random initialization vector
            val iv = ByteArray(16)
            SecureRandom().nextBytes(iv)
            val ivSpec = IvParameterSpec(iv)
            val keySpec = SecretKeySpec(SECRET_KEY.toByteArray(), "AES")
            
            // Initialize cipher for encryption
            val cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
            
            // Read input file and write encrypted data to output file
            FileInputStream(inputFile).use { fis ->
                FileOutputStream(outputFile).use { fos ->
                    // Write IV to the beginning of the output file
                    fos.write(iv)
                    
                    // Encrypt file contents
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        val encryptedData = cipher.update(buffer, 0, bytesRead)
                        if (encryptedData != null) {
                            fos.write(encryptedData)
                        }
                    }
                    
                    // Finalize encryption
                    val finalBlock = cipher.doFinal()
                    if (finalBlock != null) {
                        fos.write(finalBlock)
                    }
                }
            }
            
            // Delete original file after successful encryption
            inputFile.delete()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed: ${e.message}")
            return false
        }
    }
    
    /**
     * Decrypt a file that was encrypted using AES-256 encryption.
     *
     * @param inputFile The encrypted file
     * @param outputFile The decrypted output file
     * @return True if decryption was successful, false otherwise
     */
    fun decryptFile(inputFile: File, outputFile: File): Boolean {
        try {
            FileInputStream(inputFile).use { fis ->
                // Read IV from the beginning of the file
                val iv = ByteArray(16)
                fis.read(iv)
                val ivSpec = IvParameterSpec(iv)
                val keySpec = SecretKeySpec(SECRET_KEY.toByteArray(), "AES")
                
                // Initialize cipher for decryption
                val cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM)
                cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
                
                // Decrypt file contents
                FileOutputStream(outputFile).use { fos ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        val decryptedData = cipher.update(buffer, 0, bytesRead)
                        if (decryptedData != null) {
                            fos.write(decryptedData)
                        }
                    }
                    
                    // Finalize decryption
                    val finalBlock = cipher.doFinal()
                    if (finalBlock != null) {
                        fos.write(finalBlock)
                    }
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed: ${e.message}")
            return false
        }
    }
    
    /**
     * Manage storage by cleaning up old recordings if needed.
     *
     * @param context The application context
     * @param recordingsDir The directory containing recordings
     */
    fun manageStorage(context: Context, recordingsDir: File) {
        try {
            // Check available space
            val stat = StatFs(recordingsDir.path)
            val availableBytes = stat.availableBytes
            val availableMB = availableBytes / (1024 * 1024)
            
            Log.d(TAG, "Available storage: $availableMB MB")
            
            // If available space is less than the minimum threshold, delete oldest files
            if (availableMB < MIN_FREE_SPACE_MB) {
                // Get all files in the recordings directory
                val files = recordingsDir.listFiles() ?: return
                
                // Sort files by last modified time (oldest first)
                Arrays.sort(files) { f1, f2 ->
                    f1.lastModified().compareTo(f2.lastModified())
                }
                
                // Delete oldest files until we have enough space
                var deletedCount = 0
                for (file in files) {
                    if (file.isFile && file.delete()) {
                        val fileSizeMB = file.length() / (1024 * 1024)
                        Log.d(TAG, "Deleted old recording: ${file.name} ($fileSizeMB MB)")
                        deletedCount++
                        
                        // Check if we have enough space now
                        val newStat = StatFs(recordingsDir.path)
                        val newAvailableMB = newStat.availableBytes / (1024 * 1024)
                        if (newAvailableMB >= MIN_FREE_SPACE_MB) {
                            break
                        }
                    }
                }
                
                Log.d(TAG, "Deleted $deletedCount old recordings")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Storage management failed: ${e.message}")
        }
    }
    
    /**
     * Get the recordings directory, creating it if it doesn't exist.
     *
     * @param context The application context
     * @return The recordings directory
     */
    fun getRecordingsDirectory(context: Context): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "SecretRecorder")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
} 