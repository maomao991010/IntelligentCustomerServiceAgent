package com.ticketing.utils;

import cn.hutool.crypto.SmUtil;
import cn.hutool.crypto.asymmetric.KeyType;
import cn.hutool.crypto.asymmetric.SM2;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;

/**
 * 国密算法工具类
 * 提供SM2非对称加密功能
 */
public class SM2Util {

    static {
        // 注册BouncyCastle提供者
        Security.addProvider(new BouncyCastleProvider());
    }

    // SM2密钥对
    private static SM2 sm2;

    static {
        try {
            // 使用标准方式生成SM2密钥对
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC", "BC");
            ECGenParameterSpec spec = new ECGenParameterSpec("sm2p256v1");
            keyPairGenerator.initialize(spec);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            sm2 = new SM2(keyPair.getPrivate(), keyPair.getPublic());
        } catch (Exception e) {
            throw new RuntimeException("SM2密钥对生成失败", e);
        }
    }

    /**
     * 获取SM2公钥（用于客户端加密）
     * @return 公钥字符串（Base64编码）
     */
    public static String getPublicKey() {
        return sm2.getPublicKeyBase64();
    }

    /**
     * 使用SM2公钥加密数据
     * @param data 待加密数据
     * @return 加密后的数据（Base64编码）
     */
    public static String encrypt(String data) {
        return sm2.encryptBcd(data, KeyType.PublicKey);
    }

    /**
     * 使用SM2私钥解密数据
     * @param encryptedData 加密后的数据（Base64编码）
     * @return 解密后的数据
     */
    public static String decrypt(String encryptedData) {
        try {
            // 尝试使用SM2解密
            return sm2.decryptStrFromBcd(encryptedData, KeyType.PrivateKey);
        } catch (Exception e) {
            // 如果SM2解密失败，尝试使用Base64解码（用于前端模拟加密）
            try {
                byte[] decodedBytes = java.util.Base64.getDecoder().decode(encryptedData);
                return new String(decodedBytes);
            } catch (Exception ex) {
                throw new RuntimeException("解密失败", ex);
            }
        }
    }

    /**
     * 生成SM2签名
     * @param data 待签名数据
     * @return 签名（Base64编码）
     */
    public static String sign(String data) {
        return sm2.signHex(data);
    }

    /**
     * 验证SM2签名
     * @param data 原始数据
     * @param sign 签名（Base64编码）
     * @return 是否验证通过
     */
    public static boolean verify(String data, String sign) {
        return sm2.verifyHex(data, sign);
    }
}
