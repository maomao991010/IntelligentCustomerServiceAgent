package com.ticketing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.ticketing.dao.ElectronicTicketDao;
import com.ticketing.dao.OrderDao;
import com.ticketing.entity.ElectronicTicket;
import com.ticketing.entity.Order;
import com.ticketing.service.ElectronicTicketService;
import com.ticketing.vo.ResponseVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@Slf4j
public class ElectronicTicketServiceImpl implements ElectronicTicketService {

    @Autowired
    private ElectronicTicketDao electronicTicketDao;

    @Autowired
    private OrderDao orderDao;

    @Override
    public ResponseVo generateTicket(String orderId) {
        Order order = orderDao.selectByOrderId(orderId);
        if (order == null) {
            return ResponseVo.error(400, "订单不存在");
        }
        if (!"PAID".equals(order.getOrderStatus())) {
            return ResponseVo.error(400, "只有已支付的订单才能生成电子票");
        }

        ElectronicTicket existing = electronicTicketDao.selectByOrderId(orderId);
        if (existing != null) {
            return ResponseVo.success(existing);
        }

        String ticketCode = "TK_" + UUID.randomUUID().toString().replaceAll("-", "").substring(0, 16).toUpperCase();

        ElectronicTicket ticket = new ElectronicTicket();
        ticket.setTicketCode(ticketCode);
        ticket.setOrderId(orderId);
        ticket.setUserId(order.getUserId());
        ticket.setSessionId(order.getSessionId());
        ticket.setActivityName(order.getActivityName());
        ticket.setSessionDate(order.getSessionDate());
        ticket.setSessionTime(order.getSessionTime());
        ticket.setVenue(order.getVenue());
        ticket.setSeatInfo(order.getSeatInfo());
        ticket.setPrice(order.getTotalPrice());
        ticket.setStatus("VALID");
        ticket.setGeneratedTime(LocalDateTime.now());
        ticket.setCreateTime(LocalDateTime.now());
        ticket.setUpdateTime(LocalDateTime.now());
        electronicTicketDao.insert(ticket);

        order.setTicketCode(ticketCode);
        order.setTicketGeneratedTime(LocalDateTime.now());
        orderDao.updateById(order);

        return ResponseVo.success(ticket);
    }

    @Override
    public ResponseVo getTicketByOrderId(String orderId) {
        ElectronicTicket ticket = electronicTicketDao.selectByOrderId(orderId);
        if (ticket == null) {
            return ResponseVo.error(400, "电子票不存在");
        }
        return ResponseVo.success(ticket);
    }

    @Override
    public ResponseVo getTicketByCode(String ticketCode) {
        ElectronicTicket ticket = electronicTicketDao.selectByTicketCode(ticketCode);
        if (ticket == null) {
            return ResponseVo.error(400, "电子票不存在");
        }
        return ResponseVo.success(ticket);
    }

    @Override
    public ResponseVo useTicket(String ticketCode) {
        ElectronicTicket ticket = electronicTicketDao.selectByTicketCode(ticketCode);
        if (ticket == null) {
            return ResponseVo.error(400, "电子票不存在");
        }
        if (!"VALID".equals(ticket.getStatus())) {
            return ResponseVo.error(400, "电子票已使用或已失效");
        }
        ticket.setStatus("USED");
        ticket.setUsedTime(LocalDateTime.now());
        ticket.setUpdateTime(LocalDateTime.now());
        electronicTicketDao.updateById(ticket);
        return ResponseVo.success("验票成功");
    }

    @Override
    public byte[] generateQrCode(String content, int width, int height) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, width, height);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
            return outputStream.toByteArray();
        } catch (WriterException | java.io.IOException e) {
            log.error("生成二维码失败", e);
            return new byte[0];
        }
    }

    @Override
    public byte[] generateTicketPdf(String orderId) {
        ElectronicTicket ticket = electronicTicketDao.selectByOrderId(orderId);
        if (ticket == null) {
            return new byte[0];
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            PdfFont font = PdfFontFactory.createFont("STSong-Light", "UniGB-UCS2-H");

            document.add(new Paragraph("电子票")
                    .setFont(font)
                    .setFontSize(24)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(20));

            document.add(new Paragraph(ticket.getActivityName())
                    .setFont(font)
                    .setFontSize(18)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(30));

            float[] colWidths = {120f, 300f};
            Table table = new Table(UnitValue.createPointArray(colWidths));
            table.setWidth(420f);

            addTableRow(table, font, "票号", ticket.getTicketCode());
            addTableRow(table, font, "订单号", ticket.getOrderId());
            addTableRow(table, font, "演出日期", ticket.getSessionDate());
            addTableRow(table, font, "演出时间", ticket.getSessionTime());
            addTableRow(table, font, "演出场馆", ticket.getVenue());
            addTableRow(table, font, "座位信息", ticket.getSeatInfo());
            addTableRow(table, font, "票价", "¥" + ticket.getPrice());
            addTableRow(table, font, "状态", "VALID".equals(ticket.getStatus()) ? "有效" : "已使用");

            document.add(table);

            byte[] qrCodeBytes = generateQrCode(ticket.getTicketCode(), 150, 150);
            if (qrCodeBytes.length > 0) {
                com.itextpdf.io.image.ImageData qrImageData = com.itextpdf.io.image.ImageDataFactory.create(qrCodeBytes);
                Image qrImage = new Image(qrImageData);
                qrImage.setMarginTop(20);
                qrImage.setHorizontalAlignment(com.itextpdf.layout.properties.HorizontalAlignment.CENTER);
                document.add(qrImage);

                document.add(new Paragraph("请凭此二维码入场")
                        .setFont(font)
                        .setFontSize(12)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setMarginTop(10));
            }

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("生成PDF电子票失败", e);
            return new byte[0];
        }
    }

    private void addTableRow(Table table, PdfFont font, String label, String value) {
        Cell labelCell = new Cell().add(new Paragraph(label).setFont(font).setFontSize(12));
        labelCell.setBackgroundColor(com.itextpdf.kernel.colors.ColorConstants.LIGHT_GRAY);
        Cell valueCell = new Cell().add(new Paragraph(value != null ? value : "").setFont(font).setFontSize(12));
        table.addCell(labelCell);
        table.addCell(valueCell);
    }
}
