package com.ticketing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ticketing.dao.UserAddressDao;
import com.ticketing.entity.UserAddress;
import com.ticketing.service.UserAddressService;
import com.ticketing.vo.ResponseVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class UserAddressServiceImpl implements UserAddressService {

    @Autowired
    private UserAddressDao userAddressDao;

    @Override
    public ResponseVo getAddressList(Long userId) {
        LambdaQueryWrapper<UserAddress> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserAddress::getUserId, userId)
                .orderByDesc(UserAddress::getIsDefault)
                .orderByDesc(UserAddress::getUpdateTime);
        List<UserAddress> list = userAddressDao.selectList(wrapper);
        return ResponseVo.success(list);
    }

    @Override
    public ResponseVo getAddressById(Long userId, Long addressId) {
        UserAddress address = userAddressDao.selectById(addressId);
        if (address == null || !address.getUserId().equals(userId)) {
            return ResponseVo.error(400, "地址不存在");
        }
        return ResponseVo.success(address);
    }

    @Override
    public ResponseVo addAddress(Long userId, UserAddress address) {
        if (!StringUtils.hasText(address.getReceiverName())) {
            return ResponseVo.error(400, "收货人姓名不能为空");
        }
        if (!StringUtils.hasText(address.getReceiverPhone())) {
            return ResponseVo.error(400, "收货人手机号不能为空");
        }
        if (!StringUtils.hasText(address.getDetailAddress())) {
            return ResponseVo.error(400, "详细地址不能为空");
        }

        address.setUserId(userId);
        address.setCreateTime(LocalDateTime.now());
        address.setUpdateTime(LocalDateTime.now());

        if (address.getIsDefault() == null) {
            address.setIsDefault(0);
        }

        if (address.getIsDefault() == 1) {
            clearDefaultAddress(userId);
        }

        userAddressDao.insert(address);
        return ResponseVo.success(address);
    }

    @Override
    public ResponseVo updateAddress(Long userId, UserAddress address) {
        UserAddress existing = userAddressDao.selectById(address.getId());
        if (existing == null || !existing.getUserId().equals(userId)) {
            return ResponseVo.error(400, "地址不存在");
        }

        address.setUserId(userId);
        address.setUpdateTime(LocalDateTime.now());

        if (address.getIsDefault() != null && address.getIsDefault() == 1) {
            clearDefaultAddress(userId);
        }

        userAddressDao.updateById(address);
        return ResponseVo.success(address);
    }

    @Override
    public ResponseVo deleteAddress(Long userId, Long addressId) {
        UserAddress existing = userAddressDao.selectById(addressId);
        if (existing == null || !existing.getUserId().equals(userId)) {
            return ResponseVo.error(400, "地址不存在");
        }

        userAddressDao.deleteById(addressId);
        return ResponseVo.success("删除成功");
    }

    @Override
    public ResponseVo setDefaultAddress(Long userId, Long addressId) {
        UserAddress existing = userAddressDao.selectById(addressId);
        if (existing == null || !existing.getUserId().equals(userId)) {
            return ResponseVo.error(400, "地址不存在");
        }

        clearDefaultAddress(userId);

        existing.setIsDefault(1);
        existing.setUpdateTime(LocalDateTime.now());
        userAddressDao.updateById(existing);

        return ResponseVo.success("设置默认地址成功");
    }

    private void clearDefaultAddress(Long userId) {
        LambdaUpdateWrapper<UserAddress> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(UserAddress::getUserId, userId)
                .eq(UserAddress::getIsDefault, 1)
                .set(UserAddress::getIsDefault, 0)
                .set(UserAddress::getUpdateTime, LocalDateTime.now());
        userAddressDao.update(null, updateWrapper);
    }
}
