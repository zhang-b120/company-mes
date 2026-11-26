package com.aimes.modules.master.controller;

import com.aimes.common.annotation.DataScope;
import com.aimes.common.result.PageResult;
import com.aimes.common.result.R;
import com.aimes.common.utils.ExcelUtil;
import com.aimes.modules.master.entity.BizCustomer;
import com.aimes.modules.master.service.BizCustomerService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@RestController
@RequestMapping("/api/master/customers")
@RequiredArgsConstructor
public class BizCustomerController {

    private final BizCustomerService customerService;

    @DataScope(mapperClass = "com.aimes.modules.master.mapper.BizCustomerMapper")
    @GetMapping("/page")
    public R<PageResult<Map<String, Object>>> page(
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String customerNo,
            @RequestParam(required = false) String contactName,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String address,
            @RequestParam(required = false) String settlementType,
            @RequestParam(required = false) BigDecimal creditLimitMin,
            @RequestParam(required = false) BigDecimal creditLimitMax,
            @RequestParam(required = false) String createTimeStart,
            @RequestParam(required = false) String createTimeEnd,
            @RequestParam(required = false) String createByName,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(customerService.getPageAdvanced(customerName, status,
                customerNo, contactName, phone, email, address, settlementType,
                creditLimitMin, creditLimitMax, createTimeStart, createTimeEnd,
                createByName, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public R<BizCustomer> getById(@PathVariable Long id) {
        return R.ok(customerService.getById(id));
    }

    @PostMapping
    public R<Void> create(@RequestBody BizCustomer entity) {
        customerService.create(entity);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizCustomer entity) {
        customerService.update(id, entity);
        return R.ok();
    }

    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        customerService.updateStatus(id, body.get("status"));
        return R.ok();
    }

    @PostMapping("/batch-delete")
    public R<Void> batchDelete(@RequestBody Map<String, List<Long>> body) {
        customerService.batchDelete(body.get("ids"));
        return R.ok();
    }

    @PutMapping("/batch-status")
    public R<Void> batchUpdateStatus(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Long> ids = ((List<Number>) body.get("ids")).stream().map(Number::longValue).toList();
        String status = (String) body.get("status");
        customerService.batchUpdateStatus(ids, status);
        return R.ok();
    }

    @GetMapping("/select")
    public R<List<Map<String, Object>>> selectList() {
        return R.ok(customerService.getSelectList());
    }

    @GetMapping("/export")
    public void export(HttpServletResponse response) throws IOException {
        List<BizCustomer> list = customerService.list();
        List<String> headers = Arrays.asList("客户编号", "客户名称", "联系人", "电话", "邮箱", "地址", "结算方式", "信用额度", "状态", "备注");
        List<Function<BizCustomer, Object>> extractors = Arrays.asList(
                BizCustomer::getCustomerNo, BizCustomer::getCustomerName, BizCustomer::getContactName,
                BizCustomer::getPhone, BizCustomer::getEmail, BizCustomer::getAddress,
                BizCustomer::getSettlementType, BizCustomer::getCreditLimit,
                BizCustomer::getStatus, BizCustomer::getRemark);
        ExcelUtil.export(response, "客户列表", headers, list, extractors);
    }

    @PostMapping("/import")
    public R<String> importCustomers(@RequestParam("file") MultipartFile file) {
        List<String> headers = Arrays.asList("客户编号", "客户名称", "联系人", "电话", "邮箱", "地址", "结算方式", "信用额度", "备注");
        List<Map<String, String>> rows = ExcelUtil.parseImport(file, headers);
        int count = 0;
        for (Map<String, String> row : rows) {
            BizCustomer c = new BizCustomer();
            c.setCustomerNo(row.get("客户编号"));
            c.setCustomerName(row.get("客户名称"));
            c.setContactName(row.get("联系人"));
            c.setPhone(row.get("电话"));
            c.setEmail(row.get("邮箱"));
            c.setAddress(row.get("地址"));
            c.setSettlementType(row.get("结算方式"));
            String credit = row.get("信用额度");
            if (credit != null && !credit.isEmpty()) c.setCreditLimit(new BigDecimal(credit));
            c.setRemark(row.get("备注"));
            c.setStatus("active");
            customerService.save(c);
            count++;
        }
        return R.ok("成功导入" + count + "条客户数据");
    }

    @GetMapping("/import-template")
    public void importTemplate(HttpServletResponse response) throws IOException {
        List<String> headers = Arrays.asList("客户编号", "客户名称", "联系人", "电话", "邮箱", "地址", "结算方式", "信用额度", "备注");
        ExcelUtil.exportTemplate(response, "客户导入模板", headers);
    }
}
