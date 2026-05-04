package com.ticketing.service;

import com.ticketing.entity.UserAddress;
import com.ticketing.vo.ResponseVo;
import java.util.List;

public interface UserAddressService {
    ResponseVo getAddressList(Long userId);
    ResponseVo getAddressById(Long userId, Long addressId);
    ResponseVo addAddress(Long userId, UserAddress address);
    ResponseVo updateAddress(Long userId, UserAddress address);
    ResponseVo deleteAddress(Long userId, Long addressId);
    ResponseVo setDefaultAddress(Long userId, Long addressId);
}
