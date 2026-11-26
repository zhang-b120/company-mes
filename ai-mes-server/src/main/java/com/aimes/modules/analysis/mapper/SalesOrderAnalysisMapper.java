package com.aimes.modules.analysis.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface SalesOrderAnalysisMapper {

    /** 汇总：订单总数、总金额、平均金额、完成率 */
    @Select("SELECT COUNT(*) AS totalCount, " +
            "COALESCE(SUM(total_amount),0) AS totalAmount, " +
            "COALESCE(AVG(total_amount),0) AS avgAmount, " +
            "ROUND(SUM(CASE WHEN status IN ('completed','settled') THEN 1 ELSE 0 END)*100.0/GREATEST(COUNT(*),1),1) AS completionRate, " +
            "SUM(CASE WHEN DATE_FORMAT(create_time,'%Y-%m')=DATE_FORMAT(NOW(),'%Y-%m') THEN 1 ELSE 0 END) AS monthCount, " +
            "COALESCE(SUM(CASE WHEN DATE_FORMAT(create_time,'%Y-%m')=DATE_FORMAT(NOW(),'%Y-%m') THEN total_amount ELSE 0 END),0) AS monthAmount " +
            "FROM biz_sales_order WHERE deleted=0 AND status NOT IN ('draft','cancelled','voided')")
    Map<String, Object> getSummary();

    /** 最近12个月订单数量和金额趋势 */
    @Select("SELECT DATE_FORMAT(create_time,'%Y-%m') AS month, " +
            "COUNT(*) AS orderCount, " +
            "COALESCE(SUM(total_amount),0) AS totalAmount " +
            "FROM biz_sales_order " +
            "WHERE deleted=0 AND status NOT IN ('draft','cancelled','voided') " +
            "AND create_time>=DATE_SUB(NOW(),INTERVAL 12 MONTH) " +
            "GROUP BY DATE_FORMAT(create_time,'%Y-%m') ORDER BY month")
    List<Map<String, Object>> getOrderTrend();

    /** 订单状态分布 */
    @Select("SELECT status AS name, COUNT(*) AS value " +
            "FROM biz_sales_order WHERE deleted=0 GROUP BY status")
    List<Map<String, Object>> getStatusDistribution();

    /** 热销产品 Top10 */
    @Select("SELECT p.material_name AS name, " +
            "COALESCE(SUM(i.quantity),0) AS totalQty, " +
            "COALESCE(SUM(i.amount),0) AS totalAmount " +
            "FROM biz_sales_order_item i " +
            "JOIN biz_sales_order o ON i.order_id=o.id AND o.deleted=0 " +
            "  AND o.status NOT IN ('draft','cancelled','voided') " +
            "JOIN biz_material p ON i.product_id=p.id AND p.deleted=0 " +
            "WHERE i.deleted=0 " +
            "GROUP BY p.id, p.material_name " +
            "ORDER BY totalAmount DESC LIMIT 10")
    List<Map<String, Object>> getTopProducts();

    /** 月度营收趋势（最近12个月） */
    @Select("SELECT DATE_FORMAT(create_time,'%Y-%m') AS month, " +
            "COALESCE(SUM(total_amount),0) AS revenue, " +
            "COALESCE(SUM(payment_amount),0) AS received " +
            "FROM biz_sales_order " +
            "WHERE deleted=0 AND status NOT IN ('draft','cancelled','voided') " +
            "AND create_time>=DATE_SUB(NOW(),INTERVAL 12 MONTH) " +
            "GROUP BY DATE_FORMAT(create_time,'%Y-%m') ORDER BY month")
    List<Map<String, Object>> getMonthlyRevenue();

    /** 业务员业绩排名 */
    @Select("SELECT u.real_name AS name, " +
            "COUNT(o.id) AS orderCount, " +
            "COALESCE(SUM(o.total_amount),0) AS totalAmount " +
            "FROM biz_sales_order o " +
            "JOIN sys_user u ON o.salesman_id=u.id " +
            "WHERE o.deleted=0 AND o.status NOT IN ('draft','cancelled','voided') " +
            "GROUP BY o.salesman_id, u.real_name " +
            "ORDER BY totalAmount DESC LIMIT 10")
    List<Map<String, Object>> getSalesRanking();

    /** 客户下单排名 Top10 */
    @Select("SELECT c.customer_name AS name, " +
            "COUNT(o.id) AS orderCount, " +
            "COALESCE(SUM(o.total_amount),0) AS totalAmount " +
            "FROM biz_sales_order o " +
            "JOIN biz_customer c ON o.customer_id=c.id " +
            "WHERE o.deleted=0 AND o.status NOT IN ('draft','cancelled','voided') " +
            "GROUP BY o.customer_id, c.customer_name " +
            "ORDER BY totalAmount DESC LIMIT 10")
    List<Map<String, Object>> getCustomerRanking();

    /** 交期达成率 */
    @Select("SELECT " +
            "COUNT(*) AS totalDelivery, " +
            "SUM(CASE WHEN shipped_date IS NOT NULL AND DATE(shipped_date)<=delivery_date THEN 1 ELSE 0 END) AS onTime, " +
            "SUM(CASE WHEN shipped_date IS NOT NULL AND DATE(shipped_date)>delivery_date THEN 1 ELSE 0 END) AS late, " +
            "SUM(CASE WHEN shipped_date IS NULL AND delivery_date<CURDATE() THEN 1 ELSE 0 END) AS overdue " +
            "FROM ( " +
            "  SELECT o.delivery_date, " +
            "    CASE WHEN o.status IN ('shipped','completed','settled') THEN " +
            "      COALESCE((SELECT MAX(sr.shipped_at) FROM biz_shipment_record sr WHERE sr.order_id=o.id AND sr.deleted=0), " +
            "               (SELECT MAX(t.update_time) FROM biz_inv_task t WHERE t.inv_type='sales_out' AND t.source_id=o.id AND t.status='completed' AND t.deleted=0), " +
            "               o.update_time) " +
            "    ELSE NULL END AS shipped_date " +
            "  FROM biz_sales_order o " +
            "  WHERE o.deleted=0 AND o.delivery_date IS NOT NULL " +
            "    AND o.status NOT IN ('draft','cancelled','voided') " +
            ") t")
    Map<String, Object> getDeliveryPerformance();

    /** 订单金额区间分布 */
    @Select("SELECT " +
            "CASE " +
            "  WHEN total_amount<1000 THEN '1千以下' " +
            "  WHEN total_amount<5000 THEN '1千-5千' " +
            "  WHEN total_amount<10000 THEN '5千-1万' " +
            "  WHEN total_amount<50000 THEN '1万-5万' " +
            "  WHEN total_amount<100000 THEN '5万-10万' " +
            "  ELSE '10万以上' END AS name, " +
            "COUNT(*) AS value " +
            "FROM biz_sales_order WHERE deleted=0 AND status NOT IN ('draft','cancelled','voided') " +
            "GROUP BY name " +
            "ORDER BY FIELD(name,'1千以下','1千-5千','5千-1万','1万-5万','5万-10万','10万以上')")
    List<Map<String, Object>> getAmountDistribution();

    /** 每日订单数量（最近30天，用于热力日历） */
    @Select("SELECT DATE_FORMAT(create_time,'%Y-%m-%d') AS date, COUNT(*) AS count " +
            "FROM biz_sales_order " +
            "WHERE deleted=0 AND create_time>=DATE_SUB(NOW(),INTERVAL 30 DAY) " +
            "GROUP BY DATE_FORMAT(create_time,'%Y-%m-%d') ORDER BY date")
    List<Map<String, Object>> getDailyOrderCount();

    /** 结算方式金额分布 */
    @Select("SELECT " +
            "CASE settlement_type " +
            "  WHEN 'cash' THEN '现结' " +
            "  WHEN 'monthly_30' THEN '月结30天' " +
            "  WHEN 'monthly_60' THEN '月结60天' " +
            "  WHEN 'monthly_90' THEN '月结90天' " +
            "  ELSE COALESCE(settlement_type,'未设置') END AS name, " +
            "COALESCE(SUM(total_amount),0) AS value " +
            "FROM biz_sales_order WHERE deleted=0 AND status NOT IN ('draft','cancelled','voided') " +
            "GROUP BY settlement_type")
    List<Map<String, Object>> getSettlementAmountDistribution();
}
