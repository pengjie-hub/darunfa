package com.example.myapplication.data;

import com.example.myapplication.entity.Goods;
import com.example.myapplication.remote.ApiCallback;
import com.example.myapplication.remote.ApiUtils;
import com.example.myapplication.remote.DtoMapper;
import com.example.myapplication.remote.RetrofitClient;
import com.example.myapplication.remote.dto.CategoryDTO;
import com.example.myapplication.remote.dto.ProductDTO;

import java.util.ArrayList;
import java.util.List;

/**
 * 商品数据访问的远程实现：通过后端 HTTP 接口读取商品与分类。
 * <p>
 * <b>分类不做内存缓存。</b>
 * 后台可以随时停用 / 改名 / 新增分类，而 {@code GET /api/categories} 本身就只返回
 * 「状态=启用」的分类（见后端 {@code ProductService#listCategories}）。
 * <p>
 * 这里曾经缓存了一份 {@code categoriesCache} 且<b>永不失效</b>，造成的问题正是
 * 「后台把某个分类设为停用后，前台分类页仍然显示该分类」——
 * 因为 App 进程内一直拿着旧列表，必须杀进程重进才会消失。
 * 分类接口返回很小（当前仅 24 条），每次拉取的代价远小于一次这类「停用不生效」的排查成本。
 * 如果将来确实需要缓存，必须带 TTL 并在分类页可见时强制刷新，否则同样的坑会再来一次。
 */
public class RemoteProductRepository implements ProductRepository {

    @Override
    public void getCategories(final ApiCallback<List<String>> cb) {
        loadCategories(new ApiCallback<List<CategoryDTO>>() {
            @Override
            public void onSuccess(List<CategoryDTO> cats) {
                List<String> names = new ArrayList<>();
                for (CategoryDTO c : cats) {
                    if (c.parentId == 0) names.add(c.name);
                }
                cb.onSuccess(names);
            }

            @Override
            public void onError(int code, String message) {
                cb.onError(code, message);
            }
        });
    }

    @Override
    public void getSubCategories(final String category, final ApiCallback<List<String>> cb) {
        loadCategories(new ApiCallback<List<CategoryDTO>>() {
            @Override
            public void onSuccess(List<CategoryDTO> cats) {
                Long topId = categoryIdByName(cats, category);
                List<String> names = new ArrayList<>();
                if (topId != null) {
                    for (CategoryDTO c : cats) {
                        if (c.parentId == topId) names.add(c.name);
                    }
                }
                cb.onSuccess(names);
            }

            @Override
            public void onError(int code, String message) {
                cb.onError(code, message);
            }
        });
    }

    @Override
    public void getProductsByCategory(final String category, final ApiCallback<List<Goods>> cb) {
        loadCategories(new ApiCallback<List<CategoryDTO>>() {
            @Override
            public void onSuccess(List<CategoryDTO> cats) {
                final Long topId = categoryIdByName(cats, category);
                if (topId == null) {
                    // 名称不在后端分类表里（例如该分类刚被停用）—— 返回空列表，不报错
                    cb.onSuccess(new ArrayList<Goods>());
                    return;
                }
                // 命中「分类自身 + 其全部子分类」都算该分类下的商品：
                //  - 传的是一级分类时，既覆盖「直接挂在一级分类上」的商品，也覆盖「挂在子分类上」的商品；
                //  - 传的本身是子分类（叶子）时，children 为空，退化为只匹配自身，同样正确。
                //  旧实现只收子分类 ID，导致商品若直接挂一级分类就永远查不到，且传叶子分类名必为空列表。
                final List<Long> matchIds = new ArrayList<>();
                matchIds.add(topId);
                for (CategoryDTO c : cats) {
                    if (c.parentId == topId) matchIds.add(c.id);
                }
                ApiUtils.enqueue(RetrofitClient.api().products(null, null),
                        new ApiCallback<List<ProductDTO>>() {
                            @Override
                            public void onSuccess(List<ProductDTO> products) {
                                List<Goods> result = new ArrayList<>();
                                if (products != null) {
                                    for (ProductDTO p : products) {
                                        if (matchIds.contains(p.categoryId)) result.add(DtoMapper.toGoods(p));
                                    }
                                }
                                cb.onSuccess(result);
                            }

                            @Override
                            public void onError(int code, String message) {
                                cb.onError(code, message);
                            }
                        });
            }

            @Override
            public void onError(int code, String message) {
                cb.onError(code, message);
            }
        });
    }

    @Override
    public void getProductsByCatAndSub(final String category, final String subCategory,
                                       final ApiCallback<List<Goods>> cb) {
        loadCategories(new ApiCallback<List<CategoryDTO>>() {
            @Override
            public void onSuccess(List<CategoryDTO> cats) {
                final Long subId = categoryIdByName(cats, subCategory);
                if (subId == null) {
                    // 子分类已被停用 / 改名：当作该分类下没有商品
                    cb.onSuccess(new ArrayList<Goods>());
                    return;
                }
                ApiUtils.enqueue(RetrofitClient.api().products(subId, null),
                        new ApiCallback<List<ProductDTO>>() {
                            @Override
                            public void onSuccess(List<ProductDTO> products) {
                                cb.onSuccess(DtoMapper.toGoodsList(products));
                            }

                            @Override
                            public void onError(int code, String message) {
                                cb.onError(code, message);
                            }
                        });
            }

            @Override
            public void onError(int code, String message) {
                cb.onError(code, message);
            }
        });
    }

    @Override
    public void getProductsBySection(final String section, final ApiCallback<List<Goods>> cb) {
        ApiUtils.enqueue(RetrofitClient.api().products(null, null),
                new ApiCallback<List<ProductDTO>>() {
                    @Override
                    public void onSuccess(List<ProductDTO> products) {
                        List<ProductDTO> list = new ArrayList<>();
                        if (products != null) list.addAll(products);
                        if ("hot".equals(section)) {
                            list.sort((a, b) -> Integer.compare(b.sales, a.sales));
                        } else if ("new".equals(section)) {
                            list.sort((a, b) -> Long.compare(b.id, a.id));
                        }
                        cb.onSuccess(DtoMapper.toGoodsList(list));
                    }

                    @Override
                    public void onError(int code, String message) {
                        cb.onError(code, message);
                    }
                });
    }

    @Override
    public void getAllProducts(final ApiCallback<List<Goods>> cb) {
        ApiUtils.enqueue(RetrofitClient.api().products(null, null),
                new ApiCallback<List<ProductDTO>>() {
                    @Override
                    public void onSuccess(List<ProductDTO> products) {
                        cb.onSuccess(DtoMapper.toGoodsList(products));
                    }

                    @Override
                    public void onError(int code, String message) {
                        cb.onError(code, message);
                    }
                });
    }

    @Override
    public void searchProducts(final String keyword, final ApiCallback<List<Goods>> cb) {
        ApiUtils.enqueue(RetrofitClient.api().products(null, keyword),
                new ApiCallback<List<ProductDTO>>() {
                    @Override
                    public void onSuccess(List<ProductDTO> products) {
                        cb.onSuccess(DtoMapper.toGoodsList(products));
                    }

                    @Override
                    public void onError(int code, String message) {
                        cb.onError(code, message);
                    }
                });
    }

    @Override
    public void getProductById(int id, final ApiCallback<Goods> cb) {
        ApiUtils.enqueue(RetrofitClient.api().productDetail(id),
                new ApiCallback<ProductDTO>() {
                    @Override
                    public void onSuccess(ProductDTO product) {
                        cb.onSuccess(DtoMapper.toGoods(product));
                    }

                    @Override
                    public void onError(int code, String message) {
                        cb.onError(code, message);
                    }
                });
    }

    // ---------- 内部工具 ----------

    /** 每次都拉最新的分类列表（刻意不缓存，见类注释）。 */
    private void loadCategories(final ApiCallback<List<CategoryDTO>> cb) {
        ApiUtils.enqueue(RetrofitClient.api().categories(),
                new ApiCallback<List<CategoryDTO>>() {
                    @Override
                    public void onSuccess(List<CategoryDTO> cats) {
                        cb.onSuccess(cats != null ? cats : new ArrayList<CategoryDTO>());
                    }

                    @Override
                    public void onError(int code, String message) {
                        cb.onError(code, message);
                    }
                });
    }

    /** 在给定分类列表里按名称找 ID（分类名在后端唯一）。找不到返回 null。 */
    private static Long categoryIdByName(List<CategoryDTO> cats, String name) {
        if (cats == null || name == null) return null;
        final String key = name.trim();
        for (CategoryDTO c : cats) {
            if (key.equals(c.name)) return c.id;
        }
        return null;
    }
}
