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
        connection = DriverManager.getConnection(GmallConfig.PHOENIX_DRIVER);

    }

    // value:{"db":"","tn":"","before":{},"after":{},"type":""}
    // 1、获取广播的配置数据
    // 2、过滤字段，filterColumns
    // 核心处理方法，根据mysql配置表的信息为每条数据打上走的标签，走hbase还是kafka
    @Override
    public void processElement(JSONObject value, BroadcastProcessFunction<JSONObject, String, JSONObject>.ReadOnlyContext ctx, Collector<JSONObject> out) throws Exception {

        // 1.获取状态数据
        ReadOnlyBroadcastState<String, TableProcess> broadcastState = ctx.getBroadcastState(mapStateDescriptor);
        // 获取表明和操作类型 key: tableName-type
        String key = value.getString("tableName") + "-" + value.getString("type");
        // 取出对应的配置信息数据
        TableProcess tableProcess = broadcastState.get(key);

        if (tableProcess != null) {

            //2.过滤字段
            JSONObject data = value.getJSONObject("after");
            // 根据配置信息中提供的字段做数据过滤
            filterColumn(data, tableProcess.getSinkColumns());

            // 3.分流
            // 将输出表/主题信息写入Value
            // 向数据上追加sink_table信息
            value.put("sinkTable", tableProcess.getSinkTable());
            String sinkType = tableProcess.getSinkType();

            // 判断当前数据是写入kafka还是写入hbase
            if (TableProcess.SINK_TYPE_KAFKA.equals(sinkType)) {
                // Kafka数据,写入主流
                out.collect(value);
            } else if (TableProcess.SINK_TYPE_HBASE.equals(sinkType)) {
                // HBase数据,写入侧输出流
                ctx.output(objectOutputTag, value);
            }

        } else {
            System.out.println("该组合Key：" + key + "不存在！");
        }

    }

    /**
     * @param data        {"id":"11","tm_name":"atguigu","logo_url":"aaa"}
     * @param sinkColumns id,tm_name
     *                    {"id":"11","tm_name":"atguigu"}
     */
    private void filterColumn(JSONObject data, String sinkColumns) {
        String[] fields = sinkColumns.split(",");
        List<String> columns = Arrays.asList(fields);

        data.entrySet().removeIf(next -> !columns.contains(next.getKey()));
    }


    //value:{"db":"","tableName":"","before":{},"after":{},"type":""}
    // 1、获取并解析数据
    // 2、检查表是否存在，如果不存在则需要在phoneix中创建表(checkTable)
    // 3、写入状态，广播出去
    @Override
    public void processBroadcastElement(String value, BroadcastProcessFunction<JSONObject, String, JSONObject>.Context ctx, Collector<JSONObject> out) throws Exception {

        //1.获取并解析数据
        JSONObject jsonObject = JSON.parseObject(value);
        String data = jsonObject.getString("after");
        TableProcess tableProcess = JSON.parseObject(data, TableProcess.class);

        //建表
        if (TableProcess.SINK_TYPE_HBASE.equals(tableProcess.getSinkType())) {
            checkTable(tableProcess.getSinkTable(),
                    tableProcess.getSinkColumns(),
                    tableProcess.getSinkPk(),
                    tableProcess.getSinkExtend());
        }

        //3.写入状态,广播出去
        BroadcastState<String, TableProcess> broadcastState = ctx.getBroadcastState(mapStateDescriptor);
        String key = tableProcess.getSourceTable() + "-" + tableProcess.getOperatorType();
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
            // 给主键以及扩展字段赋默认值
            // sinkPk 主键字段
            if (sinkPk == null) {
                sinkPk = "id";
            }
            // 建表扩展
            if (sinkExtend == null) {
                sinkExtend = "";
            }

            // 封装建表sql
            StringBuffer createTableSQL = new StringBuffer("create table if not exists ")
                    .append(GmallConfig.HBASE_SCHEMA)
                    .append(".")
                    .append(sinkTable)
                    .append("(");

            // 遍历添加字段信息
            String[] fields = sinkColumns.split(",");
            for (int i = 0; i < fields.length; i++) {
                // 取出字段
                String field = fields[i];
                // 判断当前字段是否为主键
                if (sinkPk.equals(field)){
                    createTableSQL.append(field).append(" varchar primary key ");
                } else {
                    createTableSQL.append(field).append(" varchar ");
                }

                // 判断是否为最后一个字段,如果不是,则添加","
                if (i < fields.length - 1) {
                    createTableSQL.append(",");
                }

                // 建表语句
                createTableSQL.append(")").append(sinkExtend);
                System.out.println(createTableSQL);

                // 预编译SQL
                preparedStatement = connection.prepareStatement(createTableSQL.toString());

                //执行
                preparedStatement.execute();
            }
        } catch (Exception e) {
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












