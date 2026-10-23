package com.example.backend.service;

import com.example.backend.common.BusinessException;
import com.example.backend.dto.AddressRequest;
import com.example.backend.entity.Address;
import com.example.backend.repository.AddressRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 收货地址服务：增删改查 + 默认地址维护。
 * <p>
 * <b>核心不变式</b>：每个用户最多只有一个默认地址（{@code is_default = true}）。
 * 所有会改变默认标记的入口（新增、编辑、设为默认、删除）都统一走
 * {@link #applyDefault(Long, Long)} 来做「先清后置」，
 * 避免出现两个默认地址后结算时取到哪条全看 SQL 顺序。
 */
@Service
public class AddressService {

    private final AddressRepository addressRepository;

    public AddressService(AddressRepository addressRepository) {
        this.addressRepository = addressRepository;
    }

    /** 我的地址列表（默认地址排最前）。 */
    public List<Address> list(Long userId) {
        return addressRepository.findByUserIdOrderByIsDefaultDesc(userId);
    }

    /** 我的默认地址（没有则空）。 */
    public Optional<Address> getDefault(Long userId) {
        return addressRepository.findByUserIdAndIsDefaultTrue(userId);
    }

    @Transactional
    public Address create(Long userId, AddressRequest req) {
        Address a = new Address();
        a.setUserId(userId);
        apply(a, req);

        List<Address> exist = addressRepository.findByUserIdOrderByIsDefaultDesc(userId);
        boolean first = exist.isEmpty();
        // 第一条地址自动成为默认；否则按请求里的开关走（不指定就不抢默认）
        boolean wantDefault = first || Boolean.TRUE.equals(req.getIsDefault());
        a.setIsDefault(wantDefault);

        Address saved = addressRepository.save(a);
        if (wantDefault) {
            applyDefault(userId, saved.getId());
        }
        return saved;
    }

    @Transactional
    public Address update(Long userId, Long id, AddressRequest req) {
        Address a = getOwned(userId, id);
        apply(a, req);
        Address saved = addressRepository.save(a);

        // 只有显式传 true 才改默认标记；传 false / null 时保持原状，
        // 否则编辑一条地址的备注就会把它「取消默认」，很反直觉。
        if (Boolean.TRUE.equals(req.getIsDefault())) {
            applyDefault(userId, saved.getId());
        }
        return saved;
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Address a = getOwned(userId, id);
        boolean wasDefault = a.getIsDefault() != null && a.getIsDefault();
        addressRepository.delete(a);
        addressRepository.flush();

        // 删掉的是默认地址 → 把剩下的第一条顶上，别让用户「没有默认地址」
        if (wasDefault) {
            List<Address> rest = addressRepository.findByUserIdOrderByIsDefaultDesc(userId);
            if (!rest.isEmpty()) {
                applyDefault(userId, rest.get(0).getId());
            }
        }
    }

    /** 设为默认地址。 */
    @Transactional
    public Address setDefault(Long userId, Long id) {
        Address a = getOwned(userId, id);
        applyDefault(userId, id);
        return addressRepository.findById(id).orElse(a);
    }

    // ---------- 内部工具 ----------

    /** 先清掉该用户所有默认标记，再把 keepId 置为默认。 */
    private void applyDefault(Long userId, Long keepId) {
        List<Address> all = addressRepository.findByUserIdOrderByIsDefaultDesc(userId);
        for (Address a : all) {
            boolean shouldBeDefault = a.getId().equals(keepId);
            if (Boolean.valueOf(shouldBeDefault).equals(a.getIsDefault())) {
                continue;
            }
            a.setIsDefault(shouldBeDefault);
            addressRepository.save(a);
        }
    }

    private Address getOwned(Long userId, Long id) {
        Address a = addressRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "地址不存在"));
        if (!a.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作他人的地址");
        }
        return a;
    }

    private void apply(Address a, AddressRequest req) {
        a.setReceiverName(req.getReceiverName().trim());
        a.setReceiverPhone(req.getReceiverPhone().trim());
        String region = req.getRegion() == null ? null : req.getRegion().trim();
        a.setProvince(region == null || region.isEmpty() ? null : region);
        a.setDetail(req.getDetail().trim());
    }
}
