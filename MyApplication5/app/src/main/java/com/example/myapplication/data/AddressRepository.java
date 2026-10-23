package com.example.myapplication.data;

import com.example.myapplication.remote.ApiCallback;
import com.example.myapplication.remote.dto.AddressDTO;
import com.example.myapplication.remote.dto.AddressRequest;

import java.util.List;

/**
 * 收货地址数据访问接口（异步回调）。
 * <p>
 * 服务端按 token 里的 userId 隔离数据，所以这里**不需要也不允许**传 userId，
 * 否则改个参数就能读写别人的地址。
 */
public interface AddressRepository {

    /** 我的地址列表（默认地址排最前）。 */
    void list(ApiCallback<List<AddressDTO>> cb);

    /** 我的默认地址；一条都没有时回调 {@code data == null}。 */
    void getDefault(ApiCallback<AddressDTO> cb);

    /** 新增（用户的第一条会自动成为默认）。 */
    void create(AddressRequest req, ApiCallback<AddressDTO> cb);

    /** 编辑。 */
    void update(long id, AddressRequest req, ApiCallback<AddressDTO> cb);

    /** 删除（删的若是默认地址，后端会把剩余第一条顶上）。 */
    void delete(long id, ApiCallback<Boolean> cb);

    /** 设为默认地址（全局唯一）。 */
    void setDefault(long id, ApiCallback<AddressDTO> cb);
}
