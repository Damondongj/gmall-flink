package com.damon.app.dwd;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.ververica.cdc.connectors.mysql.MySQLSource;
import com.alibaba.ververica.cdc.connectors.mysql.table.StartupOptions;
import com.alibaba.ververica.cdc.debezium.DebeziumSourceFunction;
import com.damon.app.function.CustomerDeserialization;
import com.damon.app.function.DimSinkFunction;
import com.damon.app.function.TableProcessFunction;
import com.damon.bean.TableProcess;
import com.damon.utils.MyKafkaUtil;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.streaming.api.datastream.*;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kafka.KafkaSerializationSchema;
import org.apache.flink.util.OutputTag;
import org.apache.kafka.clients.producer.ProducerRecord;

import static com.damon.utils.EnvUtil.getEnv;

public class BaseDBApp {
    public static void main(String[] args) throws Exception {

        StreamExecutionEnvironment env = getEnv();

        // 2.消费Kafka ods_base_db 主题数据创建流
        // ods_base_db 是进过ods层处理过的， 通过flink cdc处理发送到ods_base_db
        String sourceTopic = "ods_base_db";
        String groupId = "base_db_app";
        // 从kafka读出主流数据 kafka中数据是
        DataStreamSource<String> kafkaDS = env.addSource(MyKafkaUtil.getKafkaConsumer(sourceTopic, groupId));

        // 3.将每行数据转换为JSON对象并过滤(delete)主流，将带delete字段删掉
        SingleOutputStreamOperator<JSONObject> jsonObjDS = kafkaDS.map(JSON::parseObject)
                .filter((FilterFunction<JSONObject>) value -> {

                    //取出数据的操作类型
                    String type = value.getString("type");

                    return !"delete".equals(type);
                });

        // 4.使用FlinkCDC读取配置信息表并处理成广播流
        DebeziumSourceFunction<String> sourceFunction = MySQLSource.<String>builder()
                .hostname("localhost")
                .port(3306)
                .username("root")
                .password("123")
                .databaseList("gmall_flink")
                .tableList("gmall_flink.table_process")
                .startupOptions(StartupOptions.initial())
                .deserializer(new CustomerDeserialization())
                .build();
        DataStreamSource<String> tableProcessStrDS = env.addSource(sourceFunction);
        MapStateDescriptor<String, TableProcess> mapStateDescriptor = new MapStateDescriptor<>("map-state", String.class, TableProcess.class);
        BroadcastStream<String> broadcastStream = tableProcessStrDS.broadcast(mapStateDescriptor);

        //5.连接主流和广播流  主流kafka(使用flink cdc获取的变更数据), 广播流(数据来自mysql， flink cdc读取)
        BroadcastConnectedStream<JSONObject, String> connectedStream = jsonObjDS.connect(broadcastStream);

        //6.分流  处理数据  广播流数据,主流数据(根据广播流数据进行处理)
        OutputTag<JSONObject> hbaseTag = new OutputTag<JSONObject>("hbase-tag") {
        };
        SingleOutputStreamOperator<JSONObject> kafka = connectedStream.process(new TableProcessFunction(hbaseTag, mapStateDescriptor));

        // 7.提取Kafka流数据和HBase流数据
        DataStream<JSONObject> hbase = kafka.getSideOutput(hbaseTag);

        // 8.将Kafka数据写入Kafka主题,将HBase数据写入Phoenix表
        kafka.print("kafka>>>>>>>>>>>>>>>>>>>>>>>>");
        hbase.print("hbase>>>>>>>>>>>>>>>>>>>>>>>>");

        // hbase是侧输出流
        hbase.addSink(new DimSinkFunction());
        // kafka写入主流
        kafka.addSink(MyKafkaUtil.getKafkaProducer((KafkaSerializationSchema<JSONObject>) (element, timestamp) -> new ProducerRecord<>(element.getString("sinkTable"),
                element.getString("after").getBytes())));
        env.execute("BaseDBApp");
    }
}
