package com.ticketing.utils;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Random;

@Slf4j
public class VerificationCodeUtil {

    private static final String CODE_CHARACTERS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int CODE_LENGTH = 4;
    private static final int WIDTH = 120;
    private static final int HEIGHT = 40;

    public static String generateCode() {
        Random random = new Random();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            int index = random.nextInt(CODE_CHARACTERS.length());
            code.append(CODE_CHARACTERS.charAt(index));
        }
        return code.toString();
    }

    public static String generateImage(String code) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);
        
        g.setFont(new Font("Arial", Font.BOLD, 24));
        
        Random random = new Random();
        int x = 15;
        for (int i = 0; i < code.length(); i++) {
            Color color = new Color(random.nextInt(150), random.nextInt(150), random.nextInt(150));
            g.setColor(color);
            g.drawString(String.valueOf(code.charAt(i)), x, 28);
            x += 25;
        }
        
        for (int i = 0; i < 5; i++) {
            Color color = new Color(random.nextInt(200), random.nextInt(200), random.nextInt(200));
            g.setColor(color);
            int x1 = random.nextInt(WIDTH);
            int y1 = random.nextInt(HEIGHT);
            int x2 = random.nextInt(WIDTH);
            int y2 = random.nextInt(HEIGHT);
            g.drawLine(x1, y1, x2, y2);
        }
        
        for (int i = 0; i < 30; i++) {
            Color color = new Color(random.nextInt(200), random.nextInt(200), random.nextInt(200));
            g.setColor(color);
            int dotX = random.nextInt(WIDTH);
            int y = random.nextInt(HEIGHT);
            g.drawOval(x, y, 2, 2);
        }
        
        g.dispose();
        
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            byte[] imageBytes = baos.toByteArray();
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(imageBytes);
        } catch (IOException e) {
            log.error("生成验证码图片失败", e);
            return "";
        }
    }
}
