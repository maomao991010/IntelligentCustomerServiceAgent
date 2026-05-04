package com.ticketing.service;

import com.ticketing.entity.ElectronicTicket;
import com.ticketing.vo.ResponseVo;

public interface ElectronicTicketService {
    ResponseVo generateTicket(String orderId);
    ResponseVo getTicketByOrderId(String orderId);
    ResponseVo getTicketByCode(String ticketCode);
    ResponseVo useTicket(String ticketCode);
    byte[] generateQrCode(String content, int width, int height);
    byte[] generateTicketPdf(String orderId);
}
