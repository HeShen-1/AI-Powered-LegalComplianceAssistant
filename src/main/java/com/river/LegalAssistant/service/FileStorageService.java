package com.river.LegalAssistant.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 文件存储服务
 * 负责文件的保存、哈希计算等文件处理操作
 */
@Service
@Slf4j
public class FileStorageService {

    @Value("${app.file.upload-dir:uploads}")
    private String baseUploadDir;

    /**
     * 保存文件并返回文件路径和哈希值
     * 
     * @param file 要保存的文件
     * @param subDir 子目录（如 "contracts"）
     * @return 文件保存结果
     * @throws IOException 文件操作异常
     * @throws NoSuchAlgorithmException 哈希算法异常
     */
    public FileStorageResult saveFile(MultipartFile file, String subDir) throws IOException, NoSuchAlgorithmException {
        // 使用流式处理计算文件哈希
        String fileHash;
        try (InputStream inputStream = file.getInputStream()) {
            fileHash = calculateFileHash(inputStream);
        }
        
        // 创建目录
        String uploadDir = baseUploadDir + "/" + subDir;
        Path uploadPath = Paths.get(uploadDir);
        
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
            log.info("创建上传目录: {}", uploadPath.toAbsolutePath());
        }
        
        // 生成文件名
        String fileName = fileHash + "_" + file.getOriginalFilename();
        Path filePath = uploadPath.resolve(fileName);
        
        // 检查文件是否已存在
        if (Files.exists(filePath)) {
            log.info("文件已存在，跳过保存: {}", filePath.toAbsolutePath());
        } else {
            // 使用流式处理保存文件，避免大文件内存问题
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, filePath);
                log.info("文件保存成功: {}, 大小: {} bytes", filePath.toAbsolutePath(), file.getSize());
            }
        }
        
        return new FileStorageResult(filePath, fileHash, fileName);
    }

    /**
     * 使用流式处理计算文件哈希值，避免大文件内存问题
     * 
     * @param inputStream 文件输入流
     * @return SHA-256 哈希值（十六进制字符串）
     * @throws IOException IO异常
     * @throws NoSuchAlgorithmException 哈希算法异常
     */
    public String calculateFileHash(InputStream inputStream) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192]; // 8KB 缓冲区
        int bytesRead;
        
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            digest.update(buffer, 0, bytesRead);
        }
        
        byte[] hashBytes = digest.digest();
        StringBuilder hexString = new StringBuilder();
        
        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        
        return hexString.toString();
    }

    /**
     * 获取文件扩展名
     * 
     * @param fileName 文件名
     * @return 文件扩展名（小写）
     */
    public String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }

    /**
     * 检查文件是否存在
     * 
     * @param filePath 文件路径
     * @return 是否存在
     */
    public boolean fileExists(Path filePath) {
        return Files.exists(filePath);
    }

    /**
     * 删除文件
     * 
     * @param filePath 文件路径
     * @return 是否删除成功
     */
    public boolean deleteFile(Path filePath) {
        try {
            boolean deleted = Files.deleteIfExists(filePath);
            if (deleted) {
                log.info("文件删除成功: {}", filePath.toAbsolutePath());
            }
            return deleted;
        } catch (IOException e) {
            log.error("删除文件失败: {}", filePath.toAbsolutePath(), e);
            return false;
        }
    }

    /**
     * 文件存储结果
     */
    public static class FileStorageResult {
        private final Path filePath;
        private final String fileHash;
        private final String storedFileName;

        public FileStorageResult(Path filePath, String fileHash, String storedFileName) {
            this.filePath = filePath;
            this.fileHash = fileHash;
            this.storedFileName = storedFileName;
        }

        public Path getFilePath() {
            return filePath;
        }

        public String getFileHash() {
            return fileHash;
        }

        public String getStoredFileName() {
            return storedFileName;
        }

        public String getAbsolutePath() {
            return filePath.toAbsolutePath().toString();
        }
    }
}
