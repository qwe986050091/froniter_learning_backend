package org.example.controller;

import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.example.annotation.RequireAuth;
import org.example.service.FrontierServiceImpl;
import org.example.thrift.common.ServiceException;
import org.example.thrift.product.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/product")
@RequireAuth
public class ProductController {

    private final FrontierServiceImpl frontierService;

    public ProductController(FrontierServiceImpl frontierService) {
        this.frontierService = frontierService;
    }

    // ==================== 分类 ====================

    @GetMapping("/categories")
    public ResponseEntity<List<Category>> listCategories(
            @RequestParam(required = false) Integer level,
            @RequestParam(required = false) Long parentId) {
        CategoryQuery q = new CategoryQuery();
        if (level != null) q.setLevel(level);
        if (parentId != null) q.setParentId(parentId);
        try {
            return ResponseEntity.ok(frontierService.listCategory(q));
        } catch (ServiceException e) {
            log.warn("listCategories failed: code={}, desc={}", e.getCode(), e.getDescription());
            return ResponseEntity.badRequest().build();
        } catch (TException e) {
            log.error("listCategories thrift error", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/categories/{id}")
    public ResponseEntity<?> getCategoryById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(frontierService.getCategoryById(id));
        } catch (ServiceException e) {
            log.warn("getCategoryById failed: id={}, code={}, desc={}", id, e.getCode(), e.getDescription());
            return ResponseEntity.badRequest().body(errorOf(e));
        } catch (TException e) {
            log.error("getCategoryById thrift error, id=" + id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/categories")
    public ResponseEntity<?> createCategory(@RequestBody Map<String, Object> body) {
        CategoryCreateRequest req = new CategoryCreateRequest();
        req.setName(str(body.get("name")));
        req.setParentId(toLong(body.get("parentId"), 0L));
        req.setLevel(toInt(body.get("level"), 1));
        if (body.get("sort") != null) req.setSort(toInt(body.get("sort"), 0));
        if (body.get("status") != null) req.setStatus(toInt(body.get("status"), 1));
        try {
            return ResponseEntity.ok(frontierService.createCategory(req));
        } catch (ServiceException e) {
            log.warn("createCategory failed: code={}, desc={}", e.getCode(), e.getDescription());
            return ResponseEntity.badRequest().body(errorOf(e));
        } catch (TException e) {
            log.error("createCategory thrift error", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PutMapping("/categories/{id}")
    public ResponseEntity<?> updateCategory(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        CategoryUpdateRequest req = new CategoryUpdateRequest();
        req.setId(id);
        req.setName(str(body.get("name")));
        if (body.get("parentId") != null) req.setParentId(toLong(body.get("parentId"), 0L));
        if (body.get("level") != null) req.setLevel(toInt(body.get("level"), 1));
        if (body.get("sort") != null) req.setSort(toInt(body.get("sort"), 0));
        if (body.get("status") != null) req.setStatus(toInt(body.get("status"), 1));
        try {
            return ResponseEntity.ok(frontierService.updateCategory(req));
        } catch (ServiceException e) {
            log.warn("updateCategory failed: id={}, code={}, desc={}", id, e.getCode(), e.getDescription());
            return ResponseEntity.badRequest().body(errorOf(e));
        } catch (TException e) {
            log.error("updateCategory thrift error, id=" + id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PatchMapping("/categories/{id}/status")
    public ResponseEntity<?> updateCategoryStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        CategoryStatusRequest req = new CategoryStatusRequest();
        req.setId(id);
        req.setStatus(toInt(body.get("status"), 0));
        try {
            boolean ok = frontierService.updateCategoryStatus(req);
            return ResponseEntity.ok(Map.of("success", ok));
        } catch (ServiceException e) {
            log.warn("updateCategoryStatus failed: id={}, code={}, desc={}", id, e.getCode(), e.getDescription());
            return ResponseEntity.badRequest().body(errorOf(e));
        } catch (TException e) {
            log.error("updateCategoryStatus thrift error, id=" + id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<?> deleteCategoryById(@PathVariable Long id) {
        try {
            boolean ok = frontierService.deleteCategoryById(id);
            return ResponseEntity.ok(Map.of("success", ok));
        } catch (ServiceException e) {
            log.warn("deleteCategoryById failed: id={}, code={}, desc={}", id, e.getCode(), e.getDescription());
            return ResponseEntity.badRequest().body(errorOf(e));
        } catch (TException e) {
            log.error("deleteCategoryById thrift error, id=" + id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ==================== 平台属性 ====================

    @GetMapping("/attrs")
    public ResponseEntity<PlatformAttrPageResult> listPlatformAttrs(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        PlatformAttrQuery q = new PlatformAttrQuery();
        if (categoryId != null) q.setCategoryId(categoryId);
        if (name != null) q.setName(name);
        if (status != null) q.setStatus(status);
        q.setPage(page);
        q.setPageSize(pageSize);
        try {
            return ResponseEntity.ok(frontierService.listPlatformAttr(q));
        } catch (ServiceException e) {
            log.warn("listPlatformAttrs failed: code={}, desc={}", e.getCode(), e.getDescription());
            return ResponseEntity.badRequest().build();
        } catch (TException e) {
            log.error("listPlatformAttrs thrift error", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/attrs/{id}")
    public ResponseEntity<?> getPlatformAttrById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(frontierService.getPlatformAttrById(id));
        } catch (ServiceException e) {
            log.warn("getPlatformAttrById failed: id={}, code={}, desc={}", id, e.getCode(), e.getDescription());
            return ResponseEntity.badRequest().body(errorOf(e));
        } catch (TException e) {
            log.error("getPlatformAttrById thrift error, id=" + id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/attrs")
    public ResponseEntity<?> createPlatformAttr(@RequestBody Map<String, Object> body) {
        PlatformAttrCreateRequest req = new PlatformAttrCreateRequest();
        req.setName(str(body.get("name")));
        req.setCategoryId(toLong(body.get("categoryId"), 0L));
        req.setValues(toStrList(body.get("values")));
        if (body.get("sort") != null) req.setSort(toInt(body.get("sort"), 0));
        if (body.get("status") != null) req.setStatus(toInt(body.get("status"), 1));
        try {
            return ResponseEntity.ok(frontierService.createPlatformAttr(req));
        } catch (ServiceException e) {
            log.warn("createPlatformAttr failed: code={}, desc={}", e.getCode(), e.getDescription());
            return ResponseEntity.badRequest().body(errorOf(e));
        } catch (TException e) {
            log.error("createPlatformAttr thrift error", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PutMapping("/attrs/{id}")
    public ResponseEntity<?> updatePlatformAttr(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        PlatformAttrUpdateRequest req = new PlatformAttrUpdateRequest();
        req.setId(id);
        req.setName(str(body.get("name")));
        if (body.get("categoryId") != null) req.setCategoryId(toLong(body.get("categoryId"), 0L));
        req.setValues(toStrList(body.get("values")));
        if (body.get("sort") != null) req.setSort(toInt(body.get("sort"), 0));
        if (body.get("status") != null) req.setStatus(toInt(body.get("status"), 1));
        try {
            return ResponseEntity.ok(frontierService.updatePlatformAttr(req));
        } catch (ServiceException e) {
            log.warn("updatePlatformAttr failed: id={}, code={}, desc={}", id, e.getCode(), e.getDescription());
            return ResponseEntity.badRequest().body(errorOf(e));
        } catch (TException e) {
            log.error("updatePlatformAttr thrift error, id=" + id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/attrs/{id}")
    public ResponseEntity<?> deletePlatformAttrById(@PathVariable Long id) {
        try {
            boolean ok = frontierService.deletePlatformAttrById(id);
            return ResponseEntity.ok(Map.of("success", ok));
        } catch (ServiceException e) {
            log.warn("deletePlatformAttrById failed: id={}, code={}, desc={}", id, e.getCode(), e.getDescription());
            return ResponseEntity.badRequest().body(errorOf(e));
        } catch (TException e) {
            log.error("deletePlatformAttrById thrift error, id=" + id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ==================== SPU ====================

    @GetMapping("/spus")
    public ResponseEntity<SpuPageResult> listSpus(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        SpuQuery q = new SpuQuery();
        if (categoryId != null) q.setCategoryId(categoryId);
        if (brandId != null) q.setBrandId(brandId);
        if (name != null) q.setName(name);
        if (status != null) q.setStatus(status);
        q.setPage(page);
        q.setPageSize(pageSize);
        try {
            return ResponseEntity.ok(frontierService.listSpu(q));
        } catch (ServiceException e) {
            log.warn("listSpus failed: code={}, desc={}", e.getCode(), e.getDescription());
            return ResponseEntity.badRequest().build();
        } catch (TException e) {
            log.error("listSpus thrift error", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/spus/{id}")
    public ResponseEntity<?> getSpuById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(frontierService.getSpuById(id));
        } catch (ServiceException e) {
            log.warn("getSpuById failed: id={}, code={}, desc={}", id, e.getCode(), e.getDescription());
            return ResponseEntity.badRequest().body(errorOf(e));
        } catch (TException e) {
            log.error("getSpuById thrift error, id=" + id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/spus")
    public ResponseEntity<?> createSpu(@RequestBody Map<String, Object> body) {
        SpuCreateRequest req = new SpuCreateRequest();
        req.setName(str(body.get("name")));
        if (body.get("description") != null) req.setDescription(str(body.get("description")));
        req.setCategoryId(toLong(body.get("categoryId"), 0L));
        if (body.get("brandId") != null) req.setBrandId(toLong(body.get("brandId"), 0L));
        if (body.get("sort") != null) req.setSort(toInt(body.get("sort"), 0));
        if (body.get("status") != null) req.setStatus(toInt(body.get("status"), 1));
        try {
            return ResponseEntity.ok(frontierService.createSpu(req));
        } catch (ServiceException e) {
            log.warn("createSpu failed: code={}, desc={}", e.getCode(), e.getDescription());
            return ResponseEntity.badRequest().body(errorOf(e));
        } catch (TException e) {
            log.error("createSpu thrift error", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PutMapping("/spus/{id}")
    public ResponseEntity<?> updateSpu(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        SpuUpdateRequest req = new SpuUpdateRequest();
        req.setId(id);
        req.setName(str(body.get("name")));
        if (body.get("description") != null) req.setDescription(str(body.get("description")));
        if (body.get("categoryId") != null) req.setCategoryId(toLong(body.get("categoryId"), 0L));
        if (body.get("brandId") != null) req.setBrandId(toLong(body.get("brandId"), 0L));
        if (body.get("sort") != null) req.setSort(toInt(body.get("sort"), 0));
        if (body.get("status") != null) req.setStatus(toInt(body.get("status"), 1));
        try {
            return ResponseEntity.ok(frontierService.updateSpu(req));
        } catch (ServiceException e) {
            log.warn("updateSpu failed: id={}, code={}, desc={}", id, e.getCode(), e.getDescription());
            return ResponseEntity.badRequest().body(errorOf(e));
        } catch (TException e) {
            log.error("updateSpu thrift error, id=" + id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PatchMapping("/spus/{id}/status")
    public ResponseEntity<?> updateSpuStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        SpuStatusRequest req = new SpuStatusRequest();
        req.setId(id);
        req.setStatus(toInt(body.get("status"), 0));
        try {
            boolean ok = frontierService.updateSpuStatus(req);
            return ResponseEntity.ok(Map.of("success", ok));
        } catch (ServiceException e) {
            log.warn("updateSpuStatus failed: id={}, code={}, desc={}", id, e.getCode(), e.getDescription());
            return ResponseEntity.badRequest().body(errorOf(e));
        } catch (TException e) {
            log.error("updateSpuStatus thrift error, id=" + id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PatchMapping("/spus/{id}/sort")
    public ResponseEntity<?> updateSpuSort(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        SpuSortRequest req = new SpuSortRequest();
        req.setId(id);
        req.setSort(toInt(body.get("sort"), 0));
        try {
            boolean ok = frontierService.updateSpuSort(req);
            return ResponseEntity.ok(Map.of("success", ok));
        } catch (ServiceException e) {
            log.warn("updateSpuSort failed: id={}, code={}, desc={}", id, e.getCode(), e.getDescription());
            return ResponseEntity.badRequest().body(errorOf(e));
        } catch (TException e) {
            log.error("updateSpuSort thrift error, id=" + id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/spus/{id}")
    public ResponseEntity<?> deleteSpuById(@PathVariable Long id) {
        try {
            boolean ok = frontierService.deleteSpuById(id);
            return ResponseEntity.ok(Map.of("success", ok));
        } catch (ServiceException e) {
            log.warn("deleteSpuById failed: id={}, code={}, desc={}", id, e.getCode(), e.getDescription());
            return ResponseEntity.badRequest().body(errorOf(e));
        } catch (TException e) {
            log.error("deleteSpuById thrift error, id=" + id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ========== helpers ==========

    private static String str(Object v) {
        return v == null ? null : v.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private static List<String> toStrList(Object v) {
        List<String> out = new ArrayList<>();
        if (v instanceof Iterable<?> iter) {
            for (Object e : iter) {
                if (e != null) out.add(e.toString().trim());
            }
        } else if (v != null) {
            out.add(v.toString().trim());
        }
        return out;
    }

    private static int toInt(Object v, int def) {
        if (v == null) return def;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(v.toString());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static long toLong(Object v, long def) {
        if (v == null) return def;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(v.toString());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static Map<String, Object> errorOf(ServiceException e) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", e.getCode());
        m.put("message", e.getDescription());
        return m;
    }
}
