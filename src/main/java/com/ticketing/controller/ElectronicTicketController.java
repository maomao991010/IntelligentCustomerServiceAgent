package com.ticketing.controller;

import com.ticketing.annotation.OperationLog;
import com.ticketing.annotation.OperationLog.OperType;
import com.ticketing.service.ElectronicTicketService;
import com.ticketing.vo.ResponseVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/tickets")
public class ElectronicTicketController {

    @Autowired
    private ElectronicTicketService electronicTicketService;

    @PostMapping("/generate/{orderId}")
    @OperationLog(value = "生成电子票", module = "订单", type = OperType.CREATE)
    public ResponseVo generateTicket(@PathVariable String orderId) {
        return electronicTicketService.generateTicket(orderId);
    }

    @GetMapping("/order/{orderId}")
    public ResponseVo getTicketByOrderId(@PathVariable String orderId) {
        return electronicTicketService.getTicketByOrderId(orderId);
    }

    @GetMapping("/code/{ticketCode}")
    public ResponseVo getTicketByCode(@PathVariable String ticketCode) {
        return electronicTicketService.getTicketByCode(ticketCode);
    }

    @PostMapping("/use/{ticketCode}")
    @OperationLog(value = "验票入场", module = "订单", type = OperType.UPDATE)
    public ResponseVo useTicket(@PathVariable String ticketCode) {
        return electronicTicketService.useTicket(ticketCode);
    }

    @GetMapping("/qrcode/{ticketCode}")
    public ResponseEntity<byte[]> getQrCode(@PathVariable String ticketCode) {
        byte[] qrCode = electronicTicketService.generateQrCode(ticketCode, 300, 300);
        if (qrCode.length == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE)
                .body(qrCode);
    }

    @GetMapping("/pdf/{orderId}")
    public ResponseEntity<byte[]> downloadTicketPdf(@PathVariable String orderId) {
        byte[] pdfBytes = electronicTicketService.generateTicketPdf(orderId);
        if (pdfBytes.length == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=ticket_" + orderId + ".pdf")
                .body(pdfBytes);
    }
}
