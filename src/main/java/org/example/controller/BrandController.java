package org.example.controller;

import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.example.annotation.RequireAuth;
import org.example.service.FrontierServiceImpl;
import org.example.thrift.brand.Brand;
import org.example.thrift.brand.BrandCreateRequest;
import org.example.thrift.brand.BrandPageResult;
import org.example.thrift.brand.BrandQuery;
import org.example.thrift.brand.BrandSortRequest;
import org.example.thrift.brand.BrandStatusRequest;
import org.example.thrift.brand.BrandUpdateRequest;
import org.example.thrift.common.ServiceException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/brand")
@RequireAuth
public class BrandController {

    private final FrontierServiceImpl frontierService;

    public BrandController(FrontierServiceImpl frontierService) {
        this.frontierService = frontierService;
    }

    /**
     * 分页查询品牌
     * GET /api/brand?name=&status=&page=&pageSize=
     */
    @GetMapping
    public ResponseEntity<BrandPageResult> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        BrandQuery query = new BrandQuery();
        if (name != null) query.setName(name);
        if (status != null) query.setStatus(status);
        query.setPage(page);
        query.setPageSize(pageSize);
        try {
            return ResponseEntity.ok(frontierService.listBrand(query));
        } catch (ServiceException e) {
            log.warn("listBrand failed: code={}, desc={}", e.getCode(), e.getDescription());
            return ResponseEntity.badRequest().build();
        } catch (TException e) {
            log.error("listBrand thrift error", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 按 ID 查品牌
     * GET /api/brand/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(frontierService.getBrandById(id));
        } catch (ServiceException e) {
            log.warn("getBrandById failed: id={}, code={}, desc={}", id, e.getCode(), e.getDescription());
            return ResponseEntity.badRequest().body(errorOf(e));
        } catch (TException e) {
            log.error("getBrandById thrift error, id=" + id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 新增品牌
     * POST /api/brand
     */
    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        BrandCreateRequest req = new BrandCreateRequest();
        req.setName(str(body.get("name")));
        req.setLogo(strOrNull(body.get("logo")));
        req.setFirstLetter(str(body.get("firstLetter")));
        req.setCategory(str(body.get("category")));
        req.setDescription(strOrNull(body.get("description")));
        if (body.get("sort") != null) req.setSort(toInt(body.get("sort"), 0));
        if (body.get("status") != null) req.setStatus(toInt(body.get("status"), 1));
        try {
            Brand created = frontierService.createBrand(req);
            return ResponseEntity.ok(created);
        } catch (ServiceException e) {
            log.warn("createBrand failed: code={}, desc={}", e.getCode(), e.getDescription());
            return ResponseEntity.badRequest().body(errorOf(e));
        } catch (TException e) {
            log.error("createBrand thrift error", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 修改品牌
     * PUT /api/brand/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        BrandUpdateRequest req = new BrandUpdateRequest();
        req.setId(id);
        req.setName(str(body.get("name")));
        req.setLogo(strOrNull(body.get("logo")));
        req.setFirstLetter(str(body.get("firstLetter")));
        req.setCategory(str(body.get("category")));
        req.setDescription(strOrNull(body.get("description")));
        if (body.get("sort") != null) req.setSort(toInt(body.get("sort"), 0));
        if (body.get("status") != null) req.setStatus(toInt(body.get("status"), 1));
        try {
            Brand updated = frontierService.updateBrand(req);
            return ResponseEntity.ok(updated);
        } catch (ServiceException e) {
            log.warn("updateBrand failed: id={}, code={}, desc={}", id, e.getCode(), e.getDescription());
            return ResponseEntity.badRequest().body(errorOf(e));
        } catch (TException e) {
            log.error("updateBrand thrift error, id=" + id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 删除品牌
     * DELETE /api/brand/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteById(@PathVariable Long id) {
        try {
            boolean ok = frontierService.deleteBrandById(id);
            return ResponseEntity.ok(Map.of("success", ok));
        } catch (ServiceException e) {
            log.warn("deleteBrandById failed: id={}, code={}, desc={}", id, e.getCode(), e.getDescription());
            return ResponseEntity.badRequest().body(errorOf(e));
        } catch (TException e) {
            log.error("deleteBrandById thrift error, id=" + id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 修改品牌状态
     * PATCH /api/brand/{id}/status
     * body: { "status": 1|0 }
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        BrandStatusRequest req = new BrandStatusRequest();
        req.setId(id);
        req.setStatus(toInt(body.get("status"), 0));
        try {
            boolean ok = frontierService.updateBrandStatus(req);
            return ResponseEntity.ok(Map.of("success", ok));
        } catch (ServiceException e) {
            log.warn("updateBrandStatus failed: id={}, code={}, desc={}", id, e.getCode(), e.getDescription());
            return ResponseEntity.badRequest().body(errorOf(e));
        } catch (TException e) {
            log.error("updateBrandStatus thrift error, id=" + id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 修改品牌排序
     * PATCH /api/brand/{id}/sort
     * body: { "sort": 1 }
     */
    @PatchMapping("/{id}/sort")
    public ResponseEntity<?> updateSort(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        BrandSortRequest req = new BrandSortRequest();
        req.setId(id);
        req.setSort(toInt(body.get("sort"), 0));
        try {
            boolean ok = frontierService.updateBrandSort(req);
            return ResponseEntity.ok(Map.of("success", ok));
        } catch (ServiceException e) {
            log.warn("updateBrandSort failed: id={}, code={}, desc={}", id, e.getCode(), e.getDescription());
            return ResponseEntity.badRequest().body(errorOf(e));
        } catch (TException e) {
            log.error("updateBrandSort thrift error, id=" + id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ========== helpers ==========
    private static String str(Object v) {
        return v == null ? null : v.toString().trim();
    }

    private static String strOrNull(Object v) {
        String s = str(v);
        return s == null || s.isEmpty() ? null : s;
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

    private static Map<String, Object> errorOf(ServiceException e) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", e.getCode());
        m.put("message", e.getDescription());
        return m;
    }
}
