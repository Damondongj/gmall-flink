package com.damon;

import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.types.Row;

public class FlinkCDCWithSQL {
    public static void main(String[] args) throws Exception {

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);


        String sql = "create table mysql_binlog (\n" +
                "        id STRING not null,\n" +
                "        tm_name STRING,\n" +
                "        logo_url STRING\n" +
                ") WITH (\n" +
                "        'connector' = 'mysql-cdc',\n" +
                "        'hostname' = 'localhost',\n" +
                "        'port' = '3306',\n" +
                "        'username' = 'root',\n" +
                "        'password' = '123',\n" +
                "        'database-name' = 'gmall_flink',\n" +
                "        'table_name' = 'base_trademark'\n" +
                "        )";

        tableEnv.executeSql(sql);

        Table table = tableEnv.sqlQuery("select * from mysql_binlog");

        DataStream<Tuple2<Boolean, Row>> retractStream = tableEnv.toRetractStream(table, Row.class);

        env.execute("FlinkCDCWithSQL");
    }
}
