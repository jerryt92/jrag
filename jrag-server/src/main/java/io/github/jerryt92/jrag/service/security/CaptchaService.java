package io.github.jerryt92.jrag.service.security;

import io.github.jerryt92.jrag.model.SlideCaptchaResp;
import io.github.jerryt92.jrag.utils.HashUtil;
import io.github.jerryt92.jrag.utils.UUIDUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
@EnableScheduling
public class CaptchaService {
    private static final Logger log = LogManager.getLogger(CaptchaService.class);
    final Font iFont;
    // 滑动验证码图片与凹槽尺寸
    private static final int SLIDE_CAPTCHA_WIDTH = 360;
    private static final int SLIDE_CAPTCHA_HEIGHT = 270;
    private static final int SLIDE_CAPTCHA_SLIDER_SIZE = 40;
    Base64.Encoder encoder = Base64.getEncoder();

    @Value("${jrag.security.captcha.expire-seconds}")
    private Long captchaExpireSeconds;

    private static final String CAPTCHA_KEY_PREFIX = "security_captcha:";

    private static class CaptchaCache {
        String code;
        Float puzzleX;
        long expireTime;
    }

    private static final ConcurrentHashMap<String, CaptchaCache> captchaCacheMap = new ConcurrentHashMap<>();

    public CaptchaService() {
        iFont = new Font("Arial", Font.PLAIN, 12);
    }

    public SlideCaptchaResp genSlideCaptcha() {
        // 凹槽背景图像
        BufferedImage puzzleImage = generateTextTextureBackground(SLIDE_CAPTCHA_WIDTH, SLIDE_CAPTCHA_HEIGHT, "Jrag", iFont);
        String code = UUIDUtil.randomUUID();
        BufferedImage sliderImage = new BufferedImage(SLIDE_CAPTCHA_SLIDER_SIZE, SLIDE_CAPTCHA_SLIDER_SIZE, BufferedImage.TYPE_INT_ARGB);
        // 背景
        Graphics2D puzzleG = puzzleImage.createGraphics();
        // 滑块
        Graphics2D sliderG = sliderImage.createGraphics();
        try {
            // 设置抗锯齿
            puzzleG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            sliderG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // 随机生成凹槽位置
            float x = (float) ((0.7 * Math.random() + 0.3) * (SLIDE_CAPTCHA_WIDTH - SLIDE_CAPTCHA_SLIDER_SIZE));
            float y = (float) ((0.8 * Math.random() + 0.2) * (SLIDE_CAPTCHA_HEIGHT - SLIDE_CAPTCHA_SLIDER_SIZE));
            // 创建凹槽路径
            GeneralPath puzzlePath = createPuzzlePath(puzzleG, x, y, SLIDE_CAPTCHA_SLIDER_SIZE);
            // 绘制凹槽
            drawPuzzle(puzzleG, puzzlePath);
            // 绘制滑块
            drawSlider(sliderG, puzzleImage, x, y, SLIDE_CAPTCHA_SLIDER_SIZE, puzzlePath);
            // 填充凹槽
            fillPuzzle(puzzleG, puzzlePath);
            // 保存图片
            SlideCaptchaResp slideCaptchaResp = new SlideCaptchaResp();
            slideCaptchaResp.setPuzzleUrl("data:image/png;base64," + encoder.encodeToString(bufferedImageToByteArray(puzzleImage, "png")));
            slideCaptchaResp.setWidth(SLIDE_CAPTCHA_WIDTH);
            slideCaptchaResp.setHeight(SLIDE_CAPTCHA_HEIGHT);
            slideCaptchaResp.setSliderUrl("data:image/png;base64," + encoder.encodeToString(bufferedImageToByteArray(sliderImage, "png")));
            slideCaptchaResp.setSliderSize(SLIDE_CAPTCHA_SLIDER_SIZE);
            slideCaptchaResp.setSliderY(y);
            log.info("puzzleX: " + x);
            String hash = HashUtil.getMessageDigest(bufferedImageToByteArray(puzzleImage, "png"), HashUtil.MdAlgorithm.SHA1);
            slideCaptchaResp.setHash((hash));
            CaptchaCache captchaCache = new CaptchaCache();
            captchaCache.code = code;
            captchaCache.puzzleX = x;
            captchaCache.expireTime = System.currentTimeMillis() + captchaExpireSeconds * 1000;
            captchaCacheMap.put(CAPTCHA_KEY_PREFIX + hash, captchaCache);
            return slideCaptchaResp;
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } finally {
            puzzleG.dispose();
            sliderG.dispose();
        }
    }

    public String verifySlideCaptchaGetClassicCaptcha(Float sliderX, String hash) {
        if (null == sliderX || null == hash) {
            return null;
        }
        CaptchaCache captchaCache = captchaCacheMap.get(CAPTCHA_KEY_PREFIX + hash);
        try {
            if (null != captchaCache) {
                if (System.currentTimeMillis() > captchaCache.expireTime) {
                    captchaCacheMap.remove(CAPTCHA_KEY_PREFIX + hash);
                    return null;
                }
                String code = captchaCache.code;
                Float puzzleX = captchaCache.puzzleX;
                if (null != puzzleX) {
                    // 如果输入的滑块位置和凹槽位置偏差在5px内，则验证成功
                    if (Math.abs(sliderX - puzzleX) < 5) {
                        captchaCache.puzzleX = null;
                        // 验证过滑块的缓存数据，删除坐标信息
                        captchaCacheMap.put(CAPTCHA_KEY_PREFIX + hash, captchaCache);
                        return code;
                    }
                }
                captchaCacheMap.remove(CAPTCHA_KEY_PREFIX + hash);
            }
        } catch (Throwable t) {
            log.error(t);
        }
        return null;
    }

    public boolean verifyCaptchaCode(String captchaCode, String hash) {
        if (null == captchaCode || null == hash) {
            return false;
        }
        CaptchaCache captchaCache = captchaCacheMap.remove(CAPTCHA_KEY_PREFIX + hash);
        try {
            if (null != captchaCache) {
                if (System.currentTimeMillis() > captchaCache.expireTime) {
                    return false;
                }
                String code = captchaCache.code;
                if (null != code) {
                    if (captchaCode.equalsIgnoreCase(code)) {
                        captchaCacheMap.remove(CAPTCHA_KEY_PREFIX + hash);
                        return true;
                    }
                }
            }
        } catch (Throwable t) {
            log.error("", t);
        }
        return false;
    }

    /**
     * 生成带有字符纹理的背景图，并添加彩色噪声纹理
     *
     * @param width  背景图宽度
     * @param height 背景图高度
     * @param text   纹理文本
     * @param font   使用的字体
     * @return 带有纹理的 BufferedImage
     */
    private BufferedImage generateTextTextureBackground(int width, int height, String text, Font font) {
        if (text == null) {
            text = "";
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        // 设置抗锯齿
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // 设置背景颜色
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, width, height);

        // 随机选择文本颜色
        Color randomColor = new Color((int) (Math.random() * 0x1000000));
        g2d.setColor(randomColor);

        // 随机选择字体样式和大小
        int fontStyle = (int) (Math.random() * 4); // 随机选择字体样式 (0-PLAIN, 1-BOLD, 2-ITALIC, 3-BOLD+ITALIC)
        int fontSize = (int) ((double) height / 6 + Math.random() * 20) + 20; // 随机字体大小 (20-40)
        Font newFont = new Font(font.getName(), fontStyle, fontSize);
        g2d.setFont(newFont);

        // 获取字体的宽度和高度
        int textWidth = g2d.getFontMetrics().stringWidth(text);
        int textHeight = g2d.getFontMetrics().getHeight();

        // 随机生成文本位置
        int x = (int) (Math.random() * (width - textWidth)); // 随机水平位置
        int y = (int) (Math.random() * (height - textHeight)) + textHeight; // 随机垂直位置

        // 绘制文本
        g2d.drawString(text, x, y);

        // 添加彩色噪声纹理
        addNoiseTexture(g2d, width, height);

        g2d.dispose();
        return image;
    }

    private void addNoiseTexture(Graphics2D g2d, int width, int height) {
        // 添加更大且更密集的彩色点
        for (int i = 0; i < 200; i++) { // 增加点的数量
            int x = (int) (Math.random() * width);
            int y = (int) (Math.random() * height);
            Color randomColor = new Color((int) (Math.random() * 0x1000000));
            g2d.setColor(randomColor);

            int diameter = 2 + (int) (Math.random() * 1); // 点的大小在 3-5 像素之间
            g2d.fillOval(x, y, diameter, diameter); // 使用圆形点
        }
        // 添加更多随机颜色的线条
        for (int i = 0; i < 20; i++) { // 增加线条数量
            int x1 = (int) (Math.random() * width);
            int y1 = (int) (Math.random() * height);
            int x2 = (int) (Math.random() * width);
            int y2 = (int) (Math.random() * height);
            Color randomColor = new Color((int) (Math.random() * 0x1000000));
            g2d.setColor(randomColor);
            g2d.drawLine(x1, y1, x2, y2);
        }
    }

    private static GeneralPath createPuzzlePath(Graphics2D g, float x, float y, float l) {
        // 绘制凹槽路径
        g.setColor(Color.darkGray);
        g.setStroke(new BasicStroke(2));
        // 创建凹槽路径
        GeneralPath path = new GeneralPath();
        // 顶部边缘
        path.moveTo(x, y);
        path.lineTo(x + l / 3, y);
        path.quadTo(x + l / 2, y + l / 5, x + (2 * l) / 3, y); // 向下凹陷
        path.lineTo(x + l, y);
        // 右侧边缘
        path.lineTo(x + l, y + l / 3);
        path.quadTo(x + l - l / 5, y + l / 2, x + l, y + (2 * l) / 3); // 向左凹陷
        path.lineTo(x + l, y + l);
        // 底部边缘
        path.lineTo(x + (2 * l) / 3, y + l);
        path.quadTo(x + l / 2, y + l - l / 5, x + l / 3, y + l); // 向上凹陷
        path.lineTo(x, y + l);
        // 左侧边缘
        path.lineTo(x, y + (2 * l) / 3);
        path.quadTo(x + l / 5, y + l / 2, x, y + l / 3); // 向右凹陷
        path.closePath();
        return path;
    }

    private static void drawPuzzle(Graphics2D g, GeneralPath path) {
        // 绘制凹槽边框
        g.draw(path);
    }

    private static void drawSlider(Graphics2D g, BufferedImage source, float x, float y, int size, GeneralPath path) {
        // 创建一个全透明的图像
        BufferedImage slider = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D sliderG = slider.createGraphics();
        try {
            sliderG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // 填充为全透明
            sliderG.setComposite(AlphaComposite.Clear);
            sliderG.fillRect(0, 0, size, size);
            // 设置为只绘制路径内的内容
            sliderG.setComposite(AlphaComposite.Src);
            // 将路径移动到 (0,0) 坐标（因为 subImage 起点是 (x,y)，而 slider 图像坐标是 (0,0)）
            GeneralPath movedPath = (GeneralPath) path.clone();
            movedPath.transform(java.awt.geom.AffineTransform.getTranslateInstance(-x, -y));
            sliderG.setClip(movedPath);
            // 从原图像裁剪出指定区域并绘制
            BufferedImage subImage = source.getSubimage((int) x, (int) y, size, size);
            sliderG.drawImage(subImage, 0, 0, null);
            // 绘制边框
            sliderG.setClip(null);
            sliderG.setStroke(new BasicStroke(2));
            sliderG.setColor(Color.GRAY);
            sliderG.draw(movedPath);
            // 将 slider 图像画到目标 g 上
            g.drawImage(slider, 0, 0, null);
        } finally {
            sliderG.dispose();
        }
    }

    private static void fillPuzzle(Graphics2D g, GeneralPath path) {
        // 填充凹槽
        g.setColor(new Color(224, 232, 245));
        g.fill(path);
    }

    private static byte[] bufferedImageToByteArray(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ImageIO.write(image, format, byteArrayOutputStream);
        return byteArrayOutputStream.toByteArray();
    }

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.HOURS)
    public void clearExpiredCaptchaCache() {
        try {
            captchaCacheMap.entrySet().removeIf(entry -> entry.getValue().expireTime < System.currentTimeMillis());
        } catch (Throwable t) {
            log.error("", t);
        }
    }
}
