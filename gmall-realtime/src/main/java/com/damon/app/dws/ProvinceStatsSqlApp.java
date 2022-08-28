package com.damon.app.dws;

import com.damon.bean.ProvinceStats;
import com.damon.utils.ClickHouseUtil;
import com.damon.utils.MyKafkaUtil;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import static com.damon.utils.EnvUtil.getEnv;


// MockDb -> Mysql -> flinkCdc -> kafka -> BaseDbApp -> kafka -> OrderWide(Redis) ->  kafka -> ProvinceStatsSqlApp -> Clickhouse
public class ProvinceStatsSqlApp {
    public static void main(String[] args) throws Exception {

        StreamExecutionEnvironment env = getEnv();

        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);

        // 使用DDL创建表，提取时间戳生成watermark
        String groupId = "province_stats";
        String orderWideTopic = "dwm_order_wide";
        tableEnv.executeSql("create table order_wide(\n" +
                "\t`province_id` BIGINT,\n" +
                "\t`province_name` STRING,\n" +
                "\t`province_area_code` STRING,\n" +
                "\t`province_iso_code` STRING,\n" +
                "\t`province_3166_2` STRING,\n" +
                "\t`order_id` BIGINT,\n" +
                "\t`split_total_amount` DECIMAL,\n" +
                "\t`create_time` STRING,\n" +
                "\t`rt` as TO_TIMESTAMP(create_time),\n" +
                "\tWATERMARK FOR rt AS rt -INTERVAL '1' SECOND \n" +
                ") with ("
                + MyKafkaUtil.getKafkaDDL(orderWideTopic, groupId) + ")");

        // 查询数据 分组，开窗，聚合
        Table table = tableEnv.sqlQuery("select\n" +
                "\tDATE_FORMAT(TUMBLE_START(rt, INTERVAL, '10' SECOND), 'yyyy-MM-dd HH:mm:ss') stt,\n" +
                "\tDATE_FORMAT(TUMBLE_END(rt, INTERVAL, '10' SECOND), 'yyyy-MM-dd HH:mm:ss') edt,\n" +
                "\tprovince_id,\n" +
                "\tprovince_name,\n" +
                "\tprovince_area_code,\n" +
                "\tprovince_iso_code,\n" +
                "\tprovince_3166_2_code,\n" +
                "\tcount(distinct order_id) order_count,\n" +
                "\tsum(split_total_amount) order_amount,\n" +
                "\tUNIX_TIMESTAMP() * 1000 ts\n" +
                "from\n" +
                "\torder_wide\n" +
                "group by\n" +
                "\tprovince_id,\n" +
                "\tprovince_name,\n" +
                "\tprovince_area_code,\n" +
                "\tprovince_iso_code,\n" +
                "\tprovince_3166_2_code,\n" +
                "\tTUMBLE(rt, INTERVAL '10' SECOND)\n");

        // 将动态表转化为流
        DataStream<ProvinceStats> provinceStatsDataStream = tableEnv.toAppendStream(table, ProvinceStats.class);

        // 写入clickhouse
        provinceStatsDataStream.print();
        provinceStatsDataStream.addSink(ClickHouseUtil.getSink("insert into province_stats values (?,?,?,?,?,?,?,?,?,?)"));

        env.execute("ProvinceStatsSqlApp");
    }
}
