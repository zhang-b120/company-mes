package com.aimes.modules.analysis.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface CustomerAnalysisMapper {

    /** 汇总：总数、活跃、本月新增、授信总额 */
    @Select("SELECT COUNT(*) AS total, " +
            "SUM(CASE WHEN status='active' THEN 1 ELSE 0 END) AS activeCount, " +
            "SUM(CASE WHEN DATE_FORMAT(create_time,'%Y-%m')=DATE_FORMAT(NOW(),'%Y-%m') THEN 1 ELSE 0 END) AS newThisMonth, " +
            "COALESCE(SUM(credit_limit),0) AS totalCredit " +
            "FROM biz_customer WHERE deleted=0")
    Map<String, Object> getSummary();

    /** 最近12个月每月新增客户数 */
    @Select("SELECT DATE_FORMAT(create_time,'%Y-%m') AS month, COUNT(*) AS count " +
            "FROM biz_customer WHERE deleted=0 AND create_time>=DATE_SUB(NOW(),INTERVAL 12 MONTH) " +
            "GROUP BY DATE_FORMAT(create_time,'%Y-%m') ORDER BY month")
    List<Map<String, Object>> getGrowthTrend();

    /** 结算方式分布 */
    @Select("SELECT settlement_type AS name, COUNT(*) AS value " +
            "FROM biz_customer WHERE deleted=0 GROUP BY settlement_type")
    List<Map<String, Object>> getSettlementDistribution();

    /** Top10 客户（按订单金额） */
    @Select("SELECT c.customer_name AS name, " +
            "COALESCE(SUM(o.total_amount),0) AS totalAmount, " +
            "COUNT(o.id) AS orderCount " +
            "FROM biz_customer c " +
            "LEFT JOIN biz_sales_order o ON c.id=o.customer_id AND o.deleted=0 " +
            "  AND o.status NOT IN ('draft','cancelled','voided') " +
            "WHERE c.deleted=0 GROUP BY c.id, c.customer_name " +
            "ORDER BY totalAmount DESC LIMIT 10")
    List<Map<String, Object>> getTopByAmount();

    /** 授信使用分析（前10个有授信额度的客户） */
    @Select("SELECT c.customer_name AS name, c.credit_limit AS creditLimit, " +
            "COALESCE(SUM(o.total_amount)-SUM(o.payment_amount),0) AS creditUsed " +
            "FROM biz_customer c " +
            "LEFT JOIN biz_sales_order o ON c.id=o.customer_id AND o.deleted=0 " +
            "  AND o.status NOT IN ('draft','cancelled','voided','completed','settled') " +
            "WHERE c.deleted=0 AND c.credit_limit>0 " +
            "GROUP BY c.id, c.customer_name, c.credit_limit " +
            "ORDER BY c.credit_limit DESC LIMIT 10")
    List<Map<String, Object>> getCreditUsage();

    /** 客户状态分布 */
    @Select("SELECT status AS name, COUNT(*) AS value " +
            "FROM biz_customer WHERE deleted=0 GROUP BY status")
    List<Map<String, Object>> getStatusDistribution();

    /** 下单频次分布 */
    @Select("SELECT " +
            "CASE " +
            "  WHEN cnt=0  THEN '0单' " +
            "  WHEN cnt<=5  THEN '1-5单' " +
            "  WHEN cnt<=10 THEN '6-10单' " +
            "  WHEN cnt<=20 THEN '11-20单' " +
            "  ELSE '20单以上' END AS name, " +
            "COUNT(*) AS value FROM (" +
            "  SELECT c.id, COUNT(o.id) AS cnt " +
            "  FROM biz_customer c " +
            "  LEFT JOIN biz_sales_order o ON c.id=o.customer_id AND o.deleted=0 " +
            "  WHERE c.deleted=0 GROUP BY c.id) t " +
            "GROUP BY name ORDER BY FIELD(name,'0单','1-5单','6-10单','11-20单','20单以上')")
    List<Map<String, Object>> getOrderFrequency();

    /** 每月订单金额趋势（按客户维度汇总，最近6个月） */
    @Select("SELECT DATE_FORMAT(o.create_time,'%Y-%m') AS month, " +
            "COUNT(DISTINCT o.customer_id) AS customerCount, " +
            "COALESCE(SUM(o.total_amount),0) AS totalAmount " +
            "FROM biz_sales_order o " +
            "WHERE o.deleted=0 AND o.status NOT IN ('draft','cancelled','voided') " +
            "AND o.create_time>=DATE_SUB(NOW(),INTERVAL 6 MONTH) " +
            "GROUP BY DATE_FORMAT(o.create_time,'%Y-%m') ORDER BY month")
    List<Map<String, Object>> getMonthlyOrderTrend();

    // ===== 客户看板 =====

    /** 客户汇总表格（销售额、回款率、月收款、年收款、欠款、开票金额） */
    @Select("SELECT c.id, c.customer_name AS name, c.customer_no AS customerNo, " +
            "COALESCE(os.orderAmount,0) AS orderAmount, " +
            "COALESCE(os.orderCount,0) AS orderCount, " +
            "COALESCE(rv.receivableAmount,0) AS receivableAmount, " +
            "COALESCE(rv.receivedAmount,0) AS receivedAmount, " +
            "COALESCE(rv.unreceivedAmount,0) AS debtAmount, " +
            "CASE WHEN COALESCE(rv.receivableAmount,0)>0 THEN ROUND(COALESCE(rv.receivedAmount,0)/rv.receivableAmount*100,1) ELSE 0 END AS paymentRate, " +
            "COALESCE(inv.invoiceAmount,0) AS invoiceAmount, " +
            "COALESCE(pm.monthPayment,0) AS monthPayment, " +
            "COALESCE(py.yearPayment,0) AS yearPayment " +
            "FROM biz_customer c " +
            "LEFT JOIN (SELECT customer_id, SUM(total_amount) AS orderAmount, COUNT(*) AS orderCount " +
            "  FROM biz_sales_order WHERE deleted=0 AND status NOT IN ('draft','cancelled','voided') GROUP BY customer_id) os ON c.id=os.customer_id " +
            "LEFT JOIN (SELECT customer_id, SUM(amount) AS receivableAmount, SUM(received_amount) AS receivedAmount, SUM(unreceived_amount) AS unreceivedAmount " +
            "  FROM biz_receivable WHERE deleted=0 GROUP BY customer_id) rv ON c.id=rv.customer_id " +
            "LEFT JOIN (SELECT o.customer_id, COALESCE(SUM(i.invoice_amount),0) AS invoiceAmount " +
            "  FROM biz_order_invoice i JOIN biz_sales_order o ON i.order_id=o.id AND o.deleted=0 " +
            "  WHERE i.deleted=0 GROUP BY o.customer_id) inv ON c.id=inv.customer_id " +
            "LEFT JOIN (SELECT r.customer_id, SUM(p.amount) AS monthPayment " +
            "  FROM biz_receivable r JOIN biz_payment_record p ON r.id=p.related_id AND p.related_type='receivable' AND p.deleted=0 " +
            "  WHERE r.deleted=0 AND DATE_FORMAT(p.payment_date,'%Y-%m')=DATE_FORMAT(NOW(),'%Y-%m') GROUP BY r.customer_id) pm ON c.id=pm.customer_id " +
            "LEFT JOIN (SELECT r.customer_id, SUM(p.amount) AS yearPayment " +
            "  FROM biz_receivable r JOIN biz_payment_record p ON r.id=p.related_id AND p.related_type='receivable' AND p.deleted=0 " +
            "  WHERE r.deleted=0 AND YEAR(p.payment_date)=YEAR(NOW()) GROUP BY r.customer_id) py ON c.id=py.customer_id " +
            "WHERE c.deleted=0 ORDER BY orderAmount DESC")
    List<Map<String, Object>> getKanbanSummaryTable();

    /** 客户欠款排名Top10 */
    @Select("SELECT c.customer_name AS name, COALESCE(SUM(r.unreceived_amount),0) AS value " +
            "FROM biz_customer c JOIN biz_receivable r ON c.id=r.customer_id AND r.deleted=0 AND r.unreceived_amount>0 " +
            "WHERE c.deleted=0 GROUP BY c.id, c.customer_name ORDER BY value DESC LIMIT 10")
    List<Map<String, Object>> getTopByDebt();

    /** 客户回款排名Top10 */
    @Select("SELECT c.customer_name AS name, COALESCE(SUM(p.amount),0) AS value " +
            "FROM biz_customer c " +
            "JOIN biz_receivable r ON c.id=r.customer_id AND r.deleted=0 " +
            "JOIN biz_payment_record p ON r.id=p.related_id AND p.related_type='receivable' AND p.deleted=0 " +
            "WHERE c.deleted=0 GROUP BY c.id, c.customer_name ORDER BY value DESC LIMIT 10")
    List<Map<String, Object>> getTopByPayment();

    /** 客户地区分布 */
    @Select("SELECT " +
            "CASE " +
            "  WHEN address LIKE '北京%' THEN '北京' " +
            "  WHEN address LIKE '上海%' THEN '上海' " +
            "  WHEN address LIKE '天津%' THEN '天津' " +
            "  WHEN address LIKE '重庆%' THEN '重庆' " +
            "  WHEN address LIKE '%省%' THEN SUBSTRING_INDEX(address,'省',1) " +
            "  WHEN address LIKE '%自治区%' THEN SUBSTRING_INDEX(address,'自治区',1) " +
            "  WHEN address IS NULL OR address='' THEN '未知' " +
            "  ELSE SUBSTRING(address,1,3) " +
            "END AS name, " +
            "COUNT(*) AS value " +
            "FROM biz_customer WHERE deleted=0 " +
            "GROUP BY name ORDER BY value DESC")
    List<Map<String, Object>> getRegionDistribution();

    // ===== 退货分析（基于 biz_return_order + biz_return_order_item）=====

    @Select("<script>" +
            "SELECT COUNT(DISTINCT r.id) AS totalCount, " +
            "COALESCE(SUM(ri.unit_price * ri.return_qty),0) AS totalAmount, " +
            "COUNT(DISTINCT r.customer_id) AS customerCount, " +
            "COUNT(DISTINCT ri.product_id) AS productCount " +
            "FROM biz_return_order r " +
            "LEFT JOIN biz_return_order_item ri ON r.id=ri.return_id AND ri.deleted=0 " +
            "WHERE r.deleted=0 AND r.status != 'rejected' " +
            "<if test='startDate != null and startDate != \"\"'>" +
            "AND r.create_time &gt;= #{startDate}" +
            "</if>" +
            "</script>")
    Map<String, Object> getReturnSummary(@Param("startDate") String startDate);

    @Select("<script>" +
            "SELECT COALESCE(r.customer_name,'其他') AS name, r.customer_id AS customerId, " +
            "COUNT(DISTINCT r.id) AS returnCount, " +
            "COALESCE(SUM(ri.unit_price * ri.return_qty),0) AS returnAmount, " +
            "COALESCE(SUM(ri.return_qty),0) AS returnQty, " +
            "DATE_FORMAT(MAX(r.create_time),'%Y-%m-%d') AS lastReturnDate " +
            "FROM biz_return_order r " +
            "LEFT JOIN biz_return_order_item ri ON r.id=ri.return_id AND ri.deleted=0 " +
            "WHERE r.deleted=0 AND r.status != 'rejected' " +
            "<if test='startDate != null and startDate != \"\"'>" +
            "AND r.create_time &gt;= #{startDate}" +
            "</if>" +
            "GROUP BY r.customer_id, r.customer_name " +
            "ORDER BY returnCount DESC LIMIT 10" +
            "</script>")
    List<Map<String, Object>> getReturnByCustomer(@Param("startDate") String startDate);

    @Select("<script>" +
            "SELECT COALESCE(ri.product_name,'其他') AS name, ri.product_id AS productId, " +
            "COUNT(DISTINCT r.id) AS returnCount, " +
            "COALESCE(SUM(ri.return_qty),0) AS returnQty, " +
            "COALESCE(SUM(ri.unit_price * ri.return_qty),0) AS returnAmount, " +
            "DATE_FORMAT(MAX(r.create_time),'%Y-%m-%d') AS lastReturnDate " +
            "FROM biz_return_order r " +
            "JOIN biz_return_order_item ri ON r.id=ri.return_id AND ri.deleted=0 " +
            "WHERE r.deleted=0 AND r.status != 'rejected' " +
            "<if test='startDate != null and startDate != \"\"'>" +
            "AND r.create_time &gt;= #{startDate}" +
            "</if>" +
            "GROUP BY ri.product_id, ri.product_name " +
            "ORDER BY returnQty DESC LIMIT 10" +
            "</script>")
    List<Map<String, Object>> getReturnByProduct(@Param("startDate") String startDate);

    @Select("SELECT DATE_FORMAT(r.create_time,'%Y-%m') AS month, " +
            "COUNT(DISTINCT r.id) AS returnCount, " +
            "COALESCE(SUM(ri.unit_price * ri.return_qty),0) AS returnAmount " +
            "FROM biz_return_order r " +
            "LEFT JOIN biz_return_order_item ri ON r.id=ri.return_id AND ri.deleted=0 " +
            "WHERE r.deleted=0 AND r.status != 'rejected' " +
            "AND r.create_time >= DATE_SUB(NOW(), INTERVAL 12 MONTH) " +
            "GROUP BY DATE_FORMAT(r.create_time,'%Y-%m') ORDER BY month")
    List<Map<String, Object>> getReturnMonthlyTrend();
}
