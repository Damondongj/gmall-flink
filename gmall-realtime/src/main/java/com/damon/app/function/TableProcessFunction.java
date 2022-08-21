package com.damon.app.function;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.damon.bean.TableProcess;
import com.damon.common.GmallConfig;
import org.apache.flink.api.common.state.BroadcastState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.state.ReadOnlyBroadcastState;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.co.BroadcastProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

// 分流  处理数据  广播流数据,主流数据(根据广播流数据进行处理)
// BroadcastProcessFunction 了解的不多，还需了解
/**
 * 这两个方法的区别在于对 broadcast state 的访问权限不同。在处理广播流元素这端，
 * 是具有读写权限的，而对于处理非广播流元素这端是只读的。 
 * 这样做的原因是，Flink 中是不存在跨 task 通讯的。
 * 所以为了保证 broadcast state 在所有的并发实例中是一致的，
 * 我们在处理广播流元素的时候给予写权限，在所有的 task 中均可以看到这些元素，
 * 并且要求对这些元素处理是一致的， 那么最终所有 task 得到的 broadcast state 是一致的。
 * 
 * 
 * 两个ctx共有的方法
 * 1、得到广播流的存储状态：ctx.getBroadcastState(MapStateDescriptor<K, V> stateDescriptor)
 * 2、查询元素的时间戳：ctx.timestamp()
 * 3、查询目前的Watermark：ctx.currentWatermark()
 * 4、目前的处理时间(processing time)：ctx.currentProcessingTime()
 * 5、产生旁路输出：ctx.output(OutputTag<X> outputTag, X value)
 * 
 */
public class TableProcessFunction extends BroadcastProcessFunction<JSONObject, String, JSONObject> {

    private final OutputTag<JSONObject> objectOutputTag;
    private final MapStateDescriptor<String, TableProcess> mapStateDescriptor;
    private Connection connection;

    public TableProcessFunction(OutputTag<JSONObject> objectOutputTag, MapStateDescriptor<String, TableProcess> mapStateDescriptor) {
        this.objectOutputTag = objectOutputTag;
        this.mapStateDescriptor = mapStateDescriptor;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        Class.forName(GmallConfig.PHOENIX_DRIVER);
        connection = DriverManager.getConnection(GmallConfig.PHOENIX_SERVER);

    }

    // value:{"db":"","tn":"","before":{},"after":{},"type":""}
    // 处理非广播流中的数据  该方法中对于broadcast state的访问权限是只读
    // 1、获取广播的配置数据
    // 2、过滤字段，filterColumns
    // 核心处理方法，根据mysql配置表的信息为每条数据打上走的标签，走hbase还是kafka
    @Override
    public void processElement(JSONObject value, ReadOnlyContext ctx, Collector<JSONObject> out) throws Exception {

        //1.获取状态数据
        // value: {"database":"gmall_flink","before":{},"after":{"name":"da","id":"bbbb"},"type":"insert","tableName":"zzzzz"}
        ReadOnlyBroadcastState<String, TableProcess> broadcastState = ctx.getBroadcastState(mapStateDescriptor);
        String key = value.getString("tableName") + "-" + value.getString("type");
        TableProcess tableProcess = broadcastState.get(key);
        //tableProcess:
        //  {sourceTable='zzzzz', operateType='insert', sinkType='hbase',
        //  sinkTable='dim_zzzzz', sinkColumns='id,name', sinkPk='id', sinkExtend='null'}

        if (tableProcess != null) {

            //2.过滤字段
            JSONObject data = value.getJSONObject("after");
            // 只要sinkColumns里面的sinkColumns字段
            filterColumn(data, tableProcess.getSinkColumns());

            //3.分流
            //将输出表/主题信息写入Value
            value.put("sinkTable", tableProcess.getSinkTable());
            String sinkType = tableProcess.getSinkType();
            if (TableProcess.SINK_TYPE_KAFKA.equals(sinkType)) {
                //Kafka数据,写入主流
                out.collect(value);
            } else if (TableProcess.SINK_TYPE_HBASE.equals(sinkType)) {
                //HBase数据,写入侧输出流
                ctx.output(objectOutputTag, value);
            }

        } else {
            // 出现这种情况的问题是在 tableProcess表中不存在对应的操作类型
            System.out.println("该组合Key：" + key + "不存在！");
        }
    }

    /**
     * @param data        {"id":"11","tm_name":"atguigu","logo_url":"aaa"}
     * @param sinkColumns id,tm_name  sinkColumns 字段来源于 mysql中TableProcess表中指定
     *                    提取data中的字段
     * 
     *                    {"id":"11","tm_name":"atguigu"}
     */
    private void filterColumn(JSONObject data, String sinkColumns) {

        String[] fields = sinkColumns.split(",");
        List<String> columns = Arrays.asList(fields);

        data.entrySet().removeIf(next -> !columns.contains(next.getKey()));

    }

    // value:{"db":"","tableName":"","before":{},"after":{},"type":""}
    // 处理广播流中的元素 该方法中对于broadcast state的访问权限是读写
    // 1、获取并解析数据
    // 2、检查表是否存在，如果不存在则需要在phoneix中创建表(checkTable)
    // 3、写入状态，广播出去
    /*
     * processBroadcastElement() 的实现必须在所有的并发实例中具有确定性的结果
     * 
     */
    @Override
    public void processBroadcastElement(String value, Context ctx, Collector<JSONObject> out) throws Exception {

        //{
        // "database":"gmall_flink",
        // "before":{},
        // "after":{"operate_type":"update","sink_type":"hbase","sink_table":"dim_zzzzz","source_table":"zzzzz","sink_pk":"id","sink_columns":"id,name"},
        // "type":"insert",
        // "tableName":"table_process"
        // }
        //1.获取并解析数据
        JSONObject jsonObject = JSON.parseObject(value);
        String data = jsonObject.getString("after");
        TableProcess tableProcess = JSON.parseObject(data, TableProcess.class);

        //2.建表
        if (TableProcess.SINK_TYPE_HBASE.equals(tableProcess.getSinkType())) {
            checkTable(tableProcess.getSinkTable(),
                    tableProcess.getSinkColumns(),
                    tableProcess.getSinkPk(),
                    tableProcess.getSinkExtend());
        }

        //3.写入状态,广播出去
        BroadcastState<String, TableProcess> broadcastState = ctx.getBroadcastState(mapStateDescriptor);
        String key = tableProcess.getSourceTable() + "-" + tableProcess.getOperateType();
        broadcastState.put(key, tableProcess);
    }

    /**
     *
     * @param sinkTable 表名 test
     * @param sinkColumns 表名字段 id, name, sex
     * @param sinkPk 表主键 id
     * @param sinkExtend 表扩展字段
     * 建表语句 : create table if not exists
     *                   db.tn(id varchar primary key,tm_name varchar) xxx;
     */
    private void checkTable(String sinkTable, String sinkColumns, String sinkPk, String sinkExtend) {

        PreparedStatement preparedStatement = null;

        try {
            if (sinkPk == null) {
                sinkPk = "id";
            }
            if (sinkExtend == null) {
                sinkExtend = "";
            }

            StringBuffer createTableSQL = new StringBuffer("create table if not exists ")
                    .append(GmallConfig.HBASE_SCHEMA)
                    .append(".")
                    .append(sinkTable)
                    .append("(");

            String[] fields = sinkColumns.split(",");

            for (int i = 0; i < fields.length; i++) {
                String field = fields[i];

                //判断是否为主键
                if (sinkPk.equals(field)) {
                    createTableSQL.append(field).append(" varchar primary key ");
                } else {
                    createTableSQL.append(field).append(" varchar ");
                }

                //判断是否为最后一个字段,如果不是,则添加","
                if (i < fields.length - 1) {
                    createTableSQL.append(",");
                }
            }

            createTableSQL.append(")").append(sinkExtend);

            //打印建表语句
            System.out.println(createTableSQL);

            //预编译SQL
            preparedStatement = connection.prepareStatement(createTableSQL.toString());

            //执行
            preparedStatement.execute();
        } catch (SQLException e) {
            throw new RuntimeException("Phoenix表" + sinkTable + "建表失败！");
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}












